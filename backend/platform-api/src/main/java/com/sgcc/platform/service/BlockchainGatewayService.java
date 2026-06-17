package com.sgcc.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgcc.platform.config.AppProperties;
import com.sgcc.platform.entity.ChainContractRegistryEntity;
import com.sgcc.platform.entity.DataResourceEntity;
import com.sgcc.platform.mapper.ChainContractRegistryMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BlockchainGatewayService {

    private final AppProperties appProperties;
    private final ChainContractRegistryMapper registryMapper;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private List<Map<String, Object>> contractAbi;
    private String contractSource;

    @PostConstruct
    public void ensureContractsReady() {
        this.contractSource = loadContractSource();
        this.contractAbi = compileContract(resolveChainBaseUrl("qingdao"));
        importServiceKey("qingdao");
        importServiceKey("weifang");
        importServiceKey("relay");
        ensureContract("qingdao");
        ensureContract("weifang");
        ensureContract("relay");
    }

    public void anchorResource(String chainName, DataResourceEntity resource, String root) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("groupId", appProperties.getBlockchain().getGroupId());
        payload.put("contractName", "SgccTrustAnchor");
        payload.put("contractAddress", ensureContract(chainName));
        payload.put("contractAbi", contractAbi);
        payload.put("funcName", "anchorResource");
        payload.put("funcParam", List.of(
                resource.getDataId(),
                resource.getRegion(),
                resource.getCid(),
                resource.getPackageHash(),
                resource.getPolicyHash(),
                resource.getOwnerDid(),
                resource.getDataType(),
                root
        ));
        payload.put("user", appProperties.getBlockchain().getServiceAddress());
        payload.put("useAes", false);
        post(resolveChainBaseUrl(chainName), "/trans/handle", payload);
    }

    public void recordAccess(String dataId, String requesterOrg, String requesterRole, boolean verified, boolean granted) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("groupId", appProperties.getBlockchain().getGroupId());
        payload.put("contractName", "SgccTrustAnchor");
        payload.put("contractAddress", ensureContract("relay"));
        payload.put("contractAbi", contractAbi);
        payload.put("funcName", "recordAccess");
        payload.put("funcParam", List.of(
                dataId,
                requesterOrg,
                requesterRole,
                String.valueOf(verified),
                String.valueOf(granted)
        ));
        payload.put("user", appProperties.getBlockchain().getServiceAddress());
        payload.put("useAes", false);
        post(resolveChainBaseUrl("relay"), "/trans/handle", payload);
    }

    public Map<String, Object> getAnchor(String chainName, String dataId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("groupId", appProperties.getBlockchain().getGroupId());
        payload.put("contractName", "SgccTrustAnchor");
        payload.put("contractAddress", ensureContract(chainName));
        payload.put("contractAbi", contractAbi);
        payload.put("funcName", "getAnchor");
        payload.put("funcParam", List.of(dataId));
        payload.put("user", appProperties.getBlockchain().getServiceAddress());
        payload.put("useAes", false);

        Object raw = post(resolveChainBaseUrl(chainName), "/trans/handle", payload);
        List<String> values = objectMapper.convertValue(raw, new TypeReference<>() {
        });

        Map<String, Object> anchor = new HashMap<>();
        anchor.put("region", values.get(0));
        anchor.put("cid", values.get(1));
        anchor.put("packageHash", values.get(2));
        anchor.put("policyHash", values.get(3));
        anchor.put("ownerDid", values.get(4));
        anchor.put("dataType", values.get(5));
        anchor.put("root", values.get(6));
        anchor.put("createdAt", values.get(7));
        anchor.put("exists", Boolean.parseBoolean(values.get(8)));
        return anchor;
    }

    private void importServiceKey(String chainName) {
        String query = String.format(
                "%s/privateKey/import?groupId=%s&privateKey=%s&userName=%s",
                resolveChainBaseUrl(chainName),
                appProperties.getBlockchain().getGroupId(),
                appProperties.getBlockchain().getServicePrivateKey(),
                appProperties.getBlockchain().getServiceUserName()
        );
        try {
            get(query);
        } catch (IllegalStateException ex) {
            // WeBASE returns "user name already exists" when the same service key has
            // already been imported before. That is an idempotent success for us.
            if (ex.getMessage() != null && ex.getMessage().contains("user name already exists")) {
                return;
            }
            throw ex;
        }
    }

    private String ensureContract(String chainName) {
        ChainContractRegistryEntity existing = registryMapper.selectOne(
                new LambdaQueryWrapper<ChainContractRegistryEntity>()
                        .eq(ChainContractRegistryEntity::getChainName, chainName)
                        .eq(ChainContractRegistryEntity::getContractName, "SgccTrustAnchor")
                        .last("limit 1")
        );
        if (existing != null) {
            return existing.getContractAddress();
        }

        Map<String, Object> compilePayload = new HashMap<>();
        compilePayload.put("groupId", appProperties.getBlockchain().getGroupId());
        compilePayload.put("contractName", "SgccTrustAnchor");
        compilePayload.put("solidityBase64", Base64.getEncoder().encodeToString(contractSource.getBytes(StandardCharsets.UTF_8)));
        Map<String, Object> compileResult = objectMapper.convertValue(
                post(resolveChainBaseUrl(chainName), "/contract/contractCompile", compilePayload),
                new TypeReference<>() {
                }
        );

        Map<String, Object> deployPayload = new HashMap<>();
        deployPayload.put("groupId", appProperties.getBlockchain().getGroupId());
        deployPayload.put("contractName", "SgccTrustAnchor");
        deployPayload.put("bytecodeBin", compileResult.get("bytecodeBin"));
        deployPayload.put("abiInfo", contractAbi);
        deployPayload.put("funcParam", List.of());
        deployPayload.put("user", appProperties.getBlockchain().getServiceAddress());
        deployPayload.put("useAes", false);
        deployPayload.put("version", "v1");
        deployPayload.put("contractSource", contractSource);

        String contractAddress = String.valueOf(post(resolveChainBaseUrl(chainName), "/contract/deploy", deployPayload));

        ChainContractRegistryEntity entity = new ChainContractRegistryEntity();
        entity.setChainName(chainName);
        entity.setContractName("SgccTrustAnchor");
        entity.setContractAddress(contractAddress);
        entity.setCreatedAt(LocalDateTime.now());
        registryMapper.insert(entity);
        return contractAddress;
    }

    private List<Map<String, Object>> compileContract(String baseUrl) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("groupId", appProperties.getBlockchain().getGroupId());
        payload.put("contractName", "SgccTrustAnchor");
        payload.put("solidityBase64", Base64.getEncoder().encodeToString(loadContractSource().getBytes(StandardCharsets.UTF_8)));
        Map<String, Object> response = objectMapper.convertValue(post(baseUrl, "/contract/contractCompile", payload), new TypeReference<>() {
        });
        try {
            return objectMapper.readValue(String.valueOf(response.get("contractAbi")), new TypeReference<>() {
            });
        } catch (IOException ex) {
            throw new IllegalStateException("failed to parse contract abi", ex);
        }
    }

    private Object post(String baseUrl, String path, Object payload) {
        try {
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("webase call failed: " + response.body());
            }
            if (response.body().startsWith("{") || response.body().startsWith("[")) {
                return objectMapper.readValue(response.body(), new TypeReference<>() {
                });
            }
            return response.body();
        } catch (IOException | InterruptedException ex) {
            throw new IllegalStateException("webase call failed", ex);
        }
    }

    private Object get(String absoluteUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(absoluteUrl))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("webase key import failed: " + response.body());
            }
            if (response.body().startsWith("{") || response.body().startsWith("[")) {
                return objectMapper.readValue(response.body(), new TypeReference<>() {
                });
            }
            return response.body();
        } catch (IOException | InterruptedException ex) {
            throw new IllegalStateException("webase key import failed", ex);
        }
    }

    private String resolveChainBaseUrl(String chainName) {
        return switch (chainName) {
            case "qingdao" -> appProperties.getBlockchain().getQingdao().getBaseUrl();
            case "weifang" -> appProperties.getBlockchain().getWeifang().getBaseUrl();
            case "relay" -> appProperties.getBlockchain().getRelay().getBaseUrl();
            default -> throw new IllegalArgumentException("unsupported chain: " + chainName);
        };
    }

    private String loadContractSource() {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("contracts/SgccTrustAnchor.sol")) {
            if (stream == null) {
                throw new IllegalStateException("contract source missing");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to read contract source", ex);
        }
    }
}
