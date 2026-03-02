package com.get401.auth.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JwtPublicKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtPublicKeyProvider.class);

    private final String X_APP_ID = "X-App-Id";
    private final String appId;
    private final String origin;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    private PublicKey cachedPublicKey;

    public JwtPublicKeyProvider(String appId, String origin, String baseUrl) {
        this.appId = appId;
        this.origin = origin;
        this.baseUrl = baseUrl != null ? baseUrl : "https://app.get401.com";
        this.httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
        this.mapper = new ObjectMapper();
    }

    public synchronized PublicKey getPublicKey() {
        if (cachedPublicKey != null) {
            return cachedPublicKey;
        }

        log.debug("fetching public key from " + this.baseUrl);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(this.baseUrl + "/v1/apps/auth/public-key"))
                    .header(X_APP_ID, this.appId)
                    .header("Origin", this.origin)
                    .GET()
                    .build();

            HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            String responseBody = response.body();
            if (responseBody == null || responseBody.isBlank()) {
                throw new RuntimeException("Empty response when fetching public key");
            }

            JsonNode root = mapper.readTree(responseBody);
            String base64Key = root.get("public_key").asText();
            cachedPublicKey = parsePublicKey(base64Key);

        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch or extract public key from response", e);
        }

        return cachedPublicKey;
    }

    private PublicKey parsePublicKey(String base64) {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64);
            X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance("Ed25519");
            return keyFactory.generatePublic(x509KeySpec);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Ed25519 public key", e);
        }
    }
}
