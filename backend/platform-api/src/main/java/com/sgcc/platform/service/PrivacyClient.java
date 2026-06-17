package com.sgcc.platform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgcc.platform.config.AppProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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

    public Map<String, Object> commitments(List<Map<String, String>> items) {
        return post("/api/privacy/commitments", Map.of("items", items));
    }

    public Map<String, Object> verify(Map<String, Object> payload) {
        return post("/api/privacy/verify", payload);
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
}
