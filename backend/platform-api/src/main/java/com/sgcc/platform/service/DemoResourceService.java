package com.sgcc.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgcc.platform.dto.AccessRequest;
import com.sgcc.platform.dto.AccessResponse;
import com.sgcc.platform.dto.ResourceDetailResponse;
import com.sgcc.platform.dto.ResourceSummaryResponse;
import com.sgcc.platform.dto.ResourceVerkleResponse;
import com.sgcc.platform.dto.SystemStatusResponse;
import com.sgcc.platform.dto.UploadRequest;
import com.sgcc.platform.dto.UploadResponse;
import com.sgcc.platform.entity.AccessAuditEntity;
import com.sgcc.platform.entity.DataResourceEntity;
import com.sgcc.platform.mapper.AccessAuditMapper;
import com.sgcc.platform.mapper.DataResourceMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import com.sgcc.platform.config.AppProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DemoResourceService {

    private final PrivacyClient privacyClient;
    private final IpfsClient ipfsClient;
    private final DataResourceMapper dataResourceMapper;
    private final AccessAuditMapper accessAuditMapper;
    private final StringRedisTemplate redisTemplate;
    private final BlockchainGatewayService blockchainGatewayService;
    private final PolicyEvaluator policyEvaluator;
    private final PostgresShadowService postgresShadowService;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public Map<String, Object> health() {
        return Map.of("status", "ok", "service", "platform-api");
    }

    public SystemStatusResponse systemStatus() {
        return SystemStatusResponse.builder()
                .platformApi("ok")
                .privacyServiceBaseUrl(appProperties.getPrivacy().getBaseUrl())
                .ipfsGatewayUrl(appProperties.getIpfs().getGatewayUrl())
                .qingdaoWebaseUrl(appProperties.getBlockchain().getQingdao().getBaseUrl())
                .weifangWebaseUrl(appProperties.getBlockchain().getWeifang().getBaseUrl())
                .relayWebaseUrl(appProperties.getBlockchain().getRelay().getBaseUrl())
                .components(Map.of(
                        "platformApi", true,
                        "privacyService", privacyClient.isHealthy(),
                        "ipfsGateway", ipfsClient.isHealthy()
                ))
                .build();
    }

    public UploadResponse upload(UploadRequest request) {
        String dataId = request.getDataId() == null || request.getDataId().isBlank()
                ? "D" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase()
                : request.getDataId();

        String region = normalizeRegion(request.getRegion());
        Map<String, Object> packageResult = buildPackage(region, dataId, request);
        String packageJson = String.valueOf(packageResult.get("packageJson"));
        String cid = ipfsClient.addJson(dataId + ".json", packageJson);

        DataResourceEntity entity = new DataResourceEntity();
        entity.setDataId(dataId);
        entity.setRegion(region);
        entity.setOwnerDid(request.getOwnerDid());
        entity.setDataType(request.getDataType());
        entity.setPolicyExpr(request.policyExpr());
        entity.setPolicyOrg(request.getPolicyOrg());
        entity.setPolicyRole(request.getPolicyRole());
        entity.setPolicyGrantStatus(request.getPolicyGrantStatus());
        entity.setCid(cid);
        // HD_i is currently defined as H(Package), which is the core off-chain fingerprint
        // used across SQL, Redis, IPFS and blockchain verification.
        entity.setHdValue(String.valueOf(packageResult.get("packageHash")));
        entity.setPackageHash(String.valueOf(packageResult.get("packageHash")));
        entity.setPolicyHash(String.valueOf(packageResult.get("policyHash")));
        entity.setDataHash(String.valueOf(packageResult.get("dataHash")));
        entity.setStatus("ACTIVE");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        dataResourceMapper.insert(entity);

        String root = refreshRegionCommitments(region);
        String relayRoot = mirrorRegionToRelay(region);
        entity.setRoot(root);
        entity.setRelayRoot(relayRoot);
        dataResourceMapper.updateById(entity);

        postgresShadowService.logUpload(entity, "cid=" + cid + ", packageHash=" + entity.getPackageHash());

        return UploadResponse.builder()
                .dataId(dataId)
                .region(region)
                .cid(cid)
                .hdValue(entity.getHdValue())
                .packageHash(entity.getPackageHash())
                .root(root)
                .relayRoot(relayRoot)
                .message("resource uploaded and anchored")
                .build();
    }

    public List<ResourceSummaryResponse> listResources() {
        return dataResourceMapper.selectList(
                        new LambdaQueryWrapper<DataResourceEntity>()
                                .orderByDesc(DataResourceEntity::getCreatedAt))
                .stream()
                .map(resource -> ResourceSummaryResponse.builder()
                        .dataId(resource.getDataId())
                        .region(resource.getRegion())
                        .ownerDid(resource.getOwnerDid())
                        .dataType(resource.getDataType())
                        .cid(resource.getCid())
                        .hdValue(resource.getHdValue())
                        .packageHash(resource.getPackageHash())
                        .root(resource.getRoot())
                        .status(resource.getStatus())
                        .build())
                .toList();
    }

    public ResourceDetailResponse getResource(String dataId) {
        DataResourceEntity resource = findResource(dataId);
        return ResourceDetailResponse.builder()
                .dataId(resource.getDataId())
                .region(resource.getRegion())
                .ownerDid(resource.getOwnerDid())
                .dataType(resource.getDataType())
                .policyExpr(resource.getPolicyExpr())
                .cid(resource.getCid())
                .hdValue(resource.getHdValue())
                .packageHash(resource.getPackageHash())
                .policyHash(resource.getPolicyHash())
                .root(resource.getRoot())
                .relayRoot(resource.getRelayRoot())
                .status(resource.getStatus())
                .build();
    }

    public AccessResponse access(AccessRequest request) {
        DataResourceEntity resource = findResource(request.getDataId());
        boolean granted = policyEvaluator.isGranted(resource, request);

        // Redis stores the Verkle-compatible proof as HD_i -> ProofD_i.
        String proofKey = proofKey(resource.getHdValue());
        String proofJson = redisTemplate.opsForValue().get(proofKey);
        if (proofJson == null) {
            proofJson = "{}";
        }

        Map<String, Object> anchor = blockchainGatewayService.getAnchor(resource.getRegion(), resource.getDataId());
        boolean verified = false;
        if (Boolean.TRUE.equals(anchor.get("exists"))
                && resource.getPackageHash().equals(anchor.get("packageHash"))
                && resource.getPolicyHash().equals(anchor.get("policyHash"))) {
            verified = Boolean.TRUE.equals(privacyClient.verify(Map.of(
                    "key", resource.getDataId(),
                    "value", resource.getHdValue(),
                    "proof", readProof(proofJson),
                    "root", anchor.get("root")
            )).get("verified"));
        }

        String plaintext = null;
        String message;
        if (granted && verified) {
            String packageJson = ipfsClient.getJson(resource.getCid());
            Map<String, Object> packagePayload = readMap(packageJson);
            Map<String, Object> cipher = castMap(packagePayload.get("cipher"));
            Map<String, Object> keyEnvelope = castMap(packagePayload.get("keyEnvelope"));
            plaintext = String.valueOf(privacyClient.decrypt(Map.of(
                    "encDataBase64", cipher.get("encDataBase64"),
                    "ivBase64", cipher.get("ivBase64"),
                    "wrappedDekBase64", keyEnvelope.get("wrappedDekBase64"),
                    "policyExpr", packagePayload.get("policyExpr")
            )).get("plaintext"));
            message = "access granted";
        } else if (!granted) {
            message = "policy denied";
        } else {
            message = "proof verification failed";
        }

        logAccess(resource, request, verified, granted, message);
        blockchainGatewayService.recordAccess(
                resource.getDataId(),
                request.getRequesterOrg(),
                request.getRequesterRole(),
                verified,
                granted
        );
        postgresShadowService.logAccess(resource.getDataId(), resource.getRegion(), message);

        return AccessResponse.builder()
                .granted(granted)
                .verified(verified)
                .message(message)
                .plaintext(plaintext)
                .hdValue(resource.getHdValue())
                .packageHash(resource.getPackageHash())
                .cid(resource.getCid())
                .root(String.valueOf(anchor.get("root")))
                .build();
    }

    public ResourceVerkleResponse getVerkle(String dataId) {
        DataResourceEntity resource = findResource(dataId);
        String proofKey = proofKey(resource.getHdValue());
        String proofJson = redisTemplate.opsForValue().get(proofKey);
        Map<String, Object> anchor = blockchainGatewayService.getAnchor(resource.getRegion(), resource.getDataId());

        return ResourceVerkleResponse.builder()
                .dataId(resource.getDataId())
                .hdValue(resource.getHdValue())
                .proofKey(proofKey)
                .proofJson(proofJson)
                .regionRoot(resource.getRoot())
                .relayRoot(resource.getRelayRoot())
                .chainRoot(String.valueOf(anchor.get("root")))
                .chainAnchorExists(Boolean.TRUE.equals(anchor.get("exists")))
                .build();
    }

    private Map<String, Object> buildPackage(String region, String dataId, UploadRequest request) {
        Map<String, Object> metadata = Map.of(
                "dataId", dataId,
                "ownerDid", request.getOwnerDid(),
                "region", region,
                "dataType", request.getDataType()
        );
        if (request.hasCiphertextPayload()) {
            return privacyClient.packageCiphertext(Map.of(
                    "metadata", metadata,
                    "encDataBase64", request.getEncDataBase64(),
                    "ivBase64", request.getIvBase64(),
                    "dekBase64", request.getDekBase64(),
                    "policyExpr", request.policyExpr(),
                    "dataHash", request.getDataHash() == null ? "" : request.getDataHash()
            ));
        }
        return privacyClient.encryptPlaintext(Map.of(
                "metadata", metadata,
                "plaintext", request.getPlaintext(),
                "policyExpr", request.policyExpr()
        ));
    }

    private String refreshRegionCommitments(String region) {
        List<DataResourceEntity> resources = listRegionResources(region);
        List<Map<String, String>> items = resources.stream()
                // Verkle input follows the document's current engineering mapping:
                // key = data_id, value = HD_i, and HD_i = H(Package).
                .map(item -> Map.of("key", item.getDataId(), "value", item.getHdValue()))
                .toList();
        Map<String, Object> commitments = privacyClient.commitments(items);
        String root = String.valueOf(commitments.get("root"));
        Map<String, Object> proofs = castMap(commitments.get("proofs"));

        for (DataResourceEntity resource : resources) {
            try {
                String proofJson = objectMapper.writeValueAsString(proofs.get(resource.getDataId()));
                String proofKey = proofKey(resource.getHdValue());
                redisTemplate.opsForValue().set(proofKey, proofJson);

                resource.setRoot(root);
                resource.setRedisProofKey(proofKey);
                resource.setUpdatedAt(LocalDateTime.now());
                dataResourceMapper.updateById(resource);
                blockchainGatewayService.anchorResource(region, resource, root);
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("failed to serialize proof", ex);
            }
        }
        return root;
    }

    private String mirrorRegionToRelay(String region) {
        List<DataResourceEntity> resources = listRegionResources(region);
        if (resources.isEmpty()) {
            return "";
        }
        String relayRoot = resources.get(0).getRoot();
        for (DataResourceEntity resource : resources) {
            resource.setRelayRoot(relayRoot);
            dataResourceMapper.update(
                    null,
                    new LambdaUpdateWrapper<DataResourceEntity>()
                            .eq(DataResourceEntity::getDataId, resource.getDataId())
                            .set(DataResourceEntity::getRelayRoot, relayRoot)
                            .set(DataResourceEntity::getUpdatedAt, LocalDateTime.now())
            );
            blockchainGatewayService.anchorResource("relay", resource, relayRoot);
        }
        return relayRoot;
    }

    private List<DataResourceEntity> listRegionResources(String region) {
        return new ArrayList<>(dataResourceMapper.selectList(
                new LambdaQueryWrapper<DataResourceEntity>()
                        .eq(DataResourceEntity::getRegion, region)
                        .eq(DataResourceEntity::getStatus, "ACTIVE")
                        .orderByAsc(DataResourceEntity::getDataId)));
    }

    private DataResourceEntity findResource(String dataId) {
        DataResourceEntity resource = dataResourceMapper.selectOne(
                new LambdaQueryWrapper<DataResourceEntity>()
                        .eq(DataResourceEntity::getDataId, dataId)
                        .last("limit 1"));
        if (resource == null) {
            throw new IllegalArgumentException("resource not found: " + dataId);
        }
        return resource;
    }

    private void logAccess(DataResourceEntity resource, AccessRequest request, boolean verified, boolean granted, String reason) {
        AccessAuditEntity entity = new AccessAuditEntity();
        entity.setDataId(resource.getDataId());
        entity.setRequesterOrg(request.getRequesterOrg());
        entity.setRequesterRole(request.getRequesterRole());
        entity.setRequesterGrantStatus(request.getRequesterGrantStatus());
        entity.setVerified(verified ? 1 : 0);
        entity.setGranted(granted ? 1 : 0);
        entity.setReason(reason);
        entity.setCreatedAt(LocalDateTime.now());
        accessAuditMapper.insert(entity);
    }

    private String normalizeRegion(String region) {
        return switch (region.toLowerCase()) {
            case "qingdao", "weifang" -> region.toLowerCase();
            default -> throw new IllegalArgumentException("unsupported region: " + region);
        };
    }

    private Object readProof(String proofJson) {
        try {
            return objectMapper.readValue(proofJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to parse redis proof", ex);
        }
    }

    private String proofKey(String hdValue) {
        return "verkle-proof:" + hdValue;
    }

    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to parse ipfs package", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}
