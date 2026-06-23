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
import com.sgcc.platform.dto.ResourceVerkleAuditResponse;
import com.sgcc.platform.dto.ResourceVerkleResponse;
import com.sgcc.platform.dto.SystemStatusResponse;
import com.sgcc.platform.dto.UploadRequest;
import com.sgcc.platform.dto.UploadResponse;
import com.sgcc.platform.entity.AccessAuditEntity;
import com.sgcc.platform.entity.DataResourceEntity;
import com.sgcc.platform.mapper.AccessAuditMapper;
import com.sgcc.platform.mapper.DataResourceMapper;
import com.sgcc.platform.verkle.CommitmentResult;
import com.sgcc.platform.verkle.StoredProofEnvelope;
import com.sgcc.platform.verkle.VerkleEngineGateway;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import com.sgcc.platform.config.AppProperties;
import org.springframework.data.redis.core.RedisCallback;
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
    private final DataSource dataSource;
    private final VerkleEngineGateway verkleEngineGateway;

    public Map<String, Object> health() {
        return Map.of("status", "ok", "service", "platform-api");
    }

    public SystemStatusResponse systemStatus() {
        boolean privacyHealthy = privacyClient.isHealthy();
        boolean ipfsGatewayHealthy = ipfsClient.isHealthy();
        boolean ipfsApiHealthy = ipfsClient.isApiHealthy();
        boolean mysqlHealthy = isMysqlHealthy();
        boolean redisHealthy = isRedisHealthy();
        boolean postgresHealthy = postgresShadowService.isHealthy();
        boolean qingdaoChainHealthy = blockchainGatewayService.isChainReadable("qingdao");
        boolean weifangChainHealthy = blockchainGatewayService.isChainReadable("weifang");
        boolean relayChainHealthy = blockchainGatewayService.isChainReadable("relay");
        Map<String, String> contractRegistry = blockchainGatewayService.contractRegistrySummary();
        Map<String, Long> contractRegistryCount = blockchainGatewayService.contractRegistryCountSummary();

        return SystemStatusResponse.builder()
                .platformApi("ok")
                .privacyServiceBaseUrl(appProperties.getPrivacy().getBaseUrl())
                .ipfsGatewayUrl(appProperties.getIpfs().getGatewayUrl())
                .qingdaoWebaseUrl(appProperties.getBlockchain().getQingdao().getBaseUrl())
                .weifangWebaseUrl(appProperties.getBlockchain().getWeifang().getBaseUrl())
                .relayWebaseUrl(appProperties.getBlockchain().getRelay().getBaseUrl())
                .components(Map.of(
                        "platformApi", true,
                        "privacyService", privacyHealthy,
                        "ipfsGateway", ipfsGatewayHealthy,
                        "ipfsApi", ipfsApiHealthy,
                        "mysql", mysqlHealthy,
                        "redis", redisHealthy,
                        "postgres", postgresHealthy
                ))
                .storageStatus(Map.of(
                        "mysql", mysqlHealthy,
                        "redis", redisHealthy,
                        "postgres", postgresHealthy,
                        "ipfsApi", ipfsApiHealthy,
                        "ipfsGateway", ipfsGatewayHealthy
                ))
                .chainStatus(Map.of(
                        "qingdao", qingdaoChainHealthy,
                        "weifang", weifangChainHealthy,
                        "relay", relayChainHealthy
                ))
                .contractRegistry(contractRegistry)
                .contractRegistryCount(contractRegistryCount)
                .crossChainContractAddressReuseDetected(hasCrossChainAddressReuse(contractRegistry))
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
        StoredProofEnvelope proofEnvelope = verkleEngineGateway.readEnvelope(proofJson);

        Map<String, Object> anchor = blockchainGatewayService.getAnchor(resource.getRegion(), resource.getDataId());
        boolean verified = false;
        if (proofEnvelope != null
                && Boolean.TRUE.equals(anchor.get("exists"))
                && resource.getPackageHash().equals(anchor.get("packageHash"))
                && resource.getPolicyHash().equals(anchor.get("policyHash"))) {
            verified = verkleEngineGateway.verify(
                    resource.getDataId(),
                    resource.getHdValue(),
                    proofEnvelope,
                    String.valueOf(anchor.get("root"))
            );
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
        StoredProofEnvelope proofEnvelope = verkleEngineGateway.readEnvelope(redisTemplate.opsForValue().get(proofKey));
        Map<String, Object> anchor = blockchainGatewayService.getAnchor(resource.getRegion(), resource.getDataId());

        return ResourceVerkleResponse.builder()
                .dataId(resource.getDataId())
                .hdValue(resource.getHdValue())
                .proofKey(proofKey)
                .proofJson(verkleEngineGateway.writeEnvelope(proofEnvelope))
                .regionRoot(resource.getRoot())
                .relayRoot(resource.getRelayRoot())
                .chainRoot(String.valueOf(anchor.get("root")))
                .chainAnchorExists(Boolean.TRUE.equals(anchor.get("exists")))
                .build();
    }

    public ResourceVerkleAuditResponse getVerkleAudit(String dataId) {
        DataResourceEntity resource = findResource(dataId);
        String proofKey = proofKey(resource.getHdValue());
        StoredProofEnvelope redisProofEnvelope = verkleEngineGateway.readEnvelope(redisTemplate.opsForValue().get(proofKey));
        boolean redisProofExists = redisProofEnvelope != null;
        String redisProofJson = verkleEngineGateway.writeEnvelope(redisProofEnvelope);

        String packageJson = ipfsClient.getJson(resource.getCid());
        Map<String, Object> packagePayload = readMap(packageJson);
        String ipfsPackageHash = sha256Hex(packageJson);
        String ipfsPolicyHash = String.valueOf(packagePayload.get("policyHash"));

        List<DataResourceEntity> resources = listRegionResources(resource.getRegion());
        List<Map<String, String>> items = resources.stream()
                .map(item -> Map.of("key", item.getDataId(), "value", item.getHdValue()))
                .toList();
        CommitmentResult rebuiltCommitments = verkleEngineGateway.commitments(items);
        String rebuiltRoot = rebuiltCommitments.getRoot();
        StoredProofEnvelope rebuiltProofEnvelope =
                verkleEngineGateway.buildEnvelope(rebuiltCommitments, resource.getDataId(), resource.getHdValue());
        String rebuiltProofJson = verkleEngineGateway.writeEnvelope(rebuiltProofEnvelope);

        Map<String, Object> regionAnchor = blockchainGatewayService.getAnchor(resource.getRegion(), resource.getDataId());
        Map<String, Object> relayAnchor = blockchainGatewayService.getAnchor("relay", resource.getDataId());
        String regionChainRoot = stringValue(regionAnchor.get("root"));
        String relayChainRoot = stringValue(relayAnchor.get("root"));

        boolean regionChainAnchorExists = Boolean.TRUE.equals(regionAnchor.get("exists"));
        boolean relayChainAnchorExists = Boolean.TRUE.equals(relayAnchor.get("exists"));
        boolean mysqlHdMatchesPackageHash = safeEquals(resource.getHdValue(), resource.getPackageHash());
        boolean mysqlPackageHashMatchesIpfsHash = safeEquals(resource.getPackageHash(), ipfsPackageHash);
        boolean mysqlPolicyHashMatchesIpfsPolicyHash = safeEquals(resource.getPolicyHash(), ipfsPolicyHash);
        boolean redisProofMatchesRebuilt = redisProofExists
                && verkleEngineGateway.equivalent(redisProofEnvelope, rebuiltProofEnvelope);

        boolean proofVerifiesAgainstMysqlRoot = redisProofExists && verifyProof(resource, redisProofEnvelope, resource.getRoot());
        boolean proofVerifiesAgainstRegionChainRoot = redisProofExists && verifyProof(resource, redisProofEnvelope, regionChainRoot);
        boolean proofVerifiesAgainstRelayChainRoot = redisProofExists && verifyProof(resource, redisProofEnvelope, relayChainRoot);

        boolean rebuiltRootMatchesMysqlRoot = safeEquals(rebuiltRoot, resource.getRoot());
        boolean rebuiltRootMatchesRegionChainRoot = safeEquals(rebuiltRoot, regionChainRoot);
        boolean mysqlRelayRootMatchesRelayChainRoot = safeEquals(resource.getRelayRoot(), relayChainRoot);

        boolean overallPassed = redisProofExists
                && mysqlHdMatchesPackageHash
                && mysqlPackageHashMatchesIpfsHash
                && mysqlPolicyHashMatchesIpfsPolicyHash
                && redisProofMatchesRebuilt
                && rebuiltRootMatchesMysqlRoot
                && rebuiltRootMatchesRegionChainRoot
                && mysqlRelayRootMatchesRelayChainRoot
                && proofVerifiesAgainstMysqlRoot
                && proofVerifiesAgainstRegionChainRoot
                && proofVerifiesAgainstRelayChainRoot
                && regionChainAnchorExists
                && relayChainAnchorExists;

        return ResourceVerkleAuditResponse.builder()
                .dataId(resource.getDataId())
                .region(resource.getRegion())
                .cid(resource.getCid())
                .hdValue(resource.getHdValue())
                .redisProofKey(proofKey)
                .mysqlPackageHash(resource.getPackageHash())
                .ipfsPackageHash(ipfsPackageHash)
                .mysqlPolicyHash(resource.getPolicyHash())
                .ipfsPolicyHash(ipfsPolicyHash)
                .mysqlRoot(resource.getRoot())
                .rebuiltRoot(rebuiltRoot)
                .regionChainRoot(regionChainRoot)
                .mysqlRelayRoot(resource.getRelayRoot())
                .relayChainRoot(relayChainRoot)
                .redisProofExists(redisProofExists)
                .regionChainAnchorExists(regionChainAnchorExists)
                .relayChainAnchorExists(relayChainAnchorExists)
                .mysqlHdMatchesPackageHash(mysqlHdMatchesPackageHash)
                .mysqlPackageHashMatchesIpfsHash(mysqlPackageHashMatchesIpfsHash)
                .mysqlPolicyHashMatchesIpfsPolicyHash(mysqlPolicyHashMatchesIpfsPolicyHash)
                .redisProofMatchesRebuilt(redisProofMatchesRebuilt)
                .rebuiltRootMatchesMysqlRoot(rebuiltRootMatchesMysqlRoot)
                .rebuiltRootMatchesRegionChainRoot(rebuiltRootMatchesRegionChainRoot)
                .mysqlRelayRootMatchesRelayChainRoot(mysqlRelayRootMatchesRelayChainRoot)
                .proofVerifiesAgainstMysqlRoot(proofVerifiesAgainstMysqlRoot)
                .proofVerifiesAgainstRegionChainRoot(proofVerifiesAgainstRegionChainRoot)
                .proofVerifiesAgainstRelayChainRoot(proofVerifiesAgainstRelayChainRoot)
                .overallPassed(overallPassed)
                .redisProofJson(redisProofJson)
                .rebuiltProofJson(rebuiltProofJson)
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
        CommitmentResult commitments = verkleEngineGateway.commitments(items);
        String root = commitments.getRoot();

        for (DataResourceEntity resource : resources) {
            StoredProofEnvelope envelope =
                    verkleEngineGateway.buildEnvelope(commitments, resource.getDataId(), resource.getHdValue());
            String proofJson = verkleEngineGateway.writeEnvelope(envelope);
            String proofKey = proofKey(resource.getHdValue());
            redisTemplate.opsForValue().set(proofKey, proofJson);

            resource.setRoot(root);
            resource.setRedisProofKey(proofKey);
            resource.setUpdatedAt(LocalDateTime.now());
            dataResourceMapper.updateById(resource);
            blockchainGatewayService.anchorResource(region, resource, root);
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

    private String proofKey(String hdValue) {
        return "verkle-proof:" + hdValue;
    }

    private boolean verifyProof(DataResourceEntity resource, StoredProofEnvelope proofEnvelope, String root) {
        if (root == null || root.isBlank()) {
            return false;
        }
        return proofEnvelope != null
                && verkleEngineGateway.verify(resource.getDataId(), resource.getHdValue(), proofEnvelope, root);
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

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize json", ex);
        }
    }

    private String sha256Hex(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte current : hash) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("sha-256 unavailable", ex);
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean safeEquals(String left, String right) {
        return left != null && left.equals(right);
    }

    private boolean isMysqlHealthy() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (SQLException ex) {
            return false;
        }
    }

    private boolean isRedisHealthy() {
        try {
            String pong = redisTemplate.execute((RedisCallback<String>) connection -> connection.ping());
            return pong != null && "PONG".equalsIgnoreCase(pong);
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean hasCrossChainAddressReuse(Map<String, String> contractRegistry) {
        Map<String, String> seen = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : contractRegistry.entrySet()) {
            String address = entry.getValue();
            if (address == null || address.isBlank()) {
                continue;
            }
            if (seen.containsKey(address)) {
                return true;
            }
            seen.put(address, entry.getKey());
        }
        return false;
    }
}
