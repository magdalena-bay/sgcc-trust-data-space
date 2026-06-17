package com.sgcc.platform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgcc.platform.config.AppProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IpfsClient {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public String addJson(String fileName, String content) {
        try {
            String boundary = "----sgcc-" + UUID.randomUUID();
            byte[] body = buildMultipartBody(boundary, fileName, content);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(appProperties.getIpfs().getApiUrl() + "/api/v0/add?pin=true"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("ipfs add failed: " + response.body());
            }
            Map<String, Object> payload = objectMapper.readValue(response.body(), new TypeReference<>() {
            });
            return String.valueOf(payload.get("Hash"));
        } catch (IOException | InterruptedException ex) {
            throw new IllegalStateException("ipfs add failed", ex);
        }
    }

    public String getJson(String cid) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(appProperties.getIpfs().getGatewayUrl() + "/ipfs/" + cid))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("ipfs get failed: " + response.body());
            }
            return response.body();
        } catch (IOException | InterruptedException ex) {
            throw new IllegalStateException("ipfs get failed", ex);
        }
    }

    public boolean isHealthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(appProperties.getIpfs().getGatewayUrl() + "/ipfs/"))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.statusCode() < 500;
        } catch (IOException | InterruptedException ex) {
            return false;
        }
    }

    private byte[] buildMultipartBody(String boundary, String fileName, String content) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        buffer.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        buffer.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        buffer.write("Content-Type: application/json\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        buffer.write(content.getBytes(StandardCharsets.UTF_8));
        buffer.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return buffer.toByteArray();
    }
}
