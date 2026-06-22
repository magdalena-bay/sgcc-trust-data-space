package com.sgcc.platform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgcc.platform.config.AppProperties;
import com.sgcc.platform.verkle.CommitmentResult;
import com.sgcc.platform.verkle.StoredProofEnvelope;
import com.sgcc.platform.verkle.VerkleProofEnvelopeCodec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PrivacyClient {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public Map<String, Object> encryptPlaintext(Map<String, Object> payload) {
        return post("/api/privacy/encrypt-plaintext", payload);
    }

    public Map<String, Object> packageCiphertext(Map<String, Object> payload) {
        return post("/api/privacy/package-ciphertext", payload);
    }

    public CommitmentResult commitments(List<Map<String, String>> items) {
        Map<String, Object> response = post("/api/privacy/commitments", Map.of("items", items));
        return CommitmentResult.builder()
                .scheme(stringValue(response.get("scheme"), VerkleProofEnvelopeCodec.DEMO_SCHEME))
                .engineVersion(stringValue(response.get("engineVersion"), VerkleProofEnvelopeCodec.DEMO_ENGINE_VERSION))
                .root(stringValue(response.get("root"), ""))
                .proofByKey(castObjectMap(response.get("proofs")))
                .leafHashes(castStringMap(response.get("leafHashes")))
                .build();
    }

    public boolean verify(String key, String value, StoredProofEnvelope proofEnvelope, String root) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("key", key);
        payload.put("value", value);
        payload.put("proof", proofEnvelope.getProofPayload());
        payload.put("root", root);
        payload.put("scheme", proofEnvelope.getScheme());
        payload.put("engineVersion", proofEnvelope.getEngineVersion());
        return Boolean.TRUE.equals(post("/api/privacy/verify", payload).get("verified"));
    }

    public Map<String, Object> decrypt(Map<String, Object> payload) {
        return post("/api/privacy/decrypt", payload);
    }

    public boolean isHealthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(appProperties.getPrivacy().getBaseUrl() + "/health"))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.statusCode() < 400 && response.body().contains("\"ok\"");
        } catch (IOException | InterruptedException ex) {
            return false;
        }
    }

    private Map<String, Object> post(String path, Object payload) {
        try {
            String requestBody = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(appProperties.getPrivacy().getBaseUrl() + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("privacy-service call failed: " + response.body());
            }
            return objectMapper.readValue(response.body(), new TypeReference<>() {
            });
        } catch (IOException | InterruptedException ex) {
            throw new IllegalStateException("privacy-service call failed", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castObjectMap(Object value) {
        return value == null ? Map.of() : (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> castStringMap(Object value) {
        return value == null ? Map.of() : (Map<String, String>) value;
    }

    private String stringValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String stringValue = String.valueOf(value);
        return stringValue.isBlank() ? fallback : stringValue;
    }
}
