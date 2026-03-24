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
import java.time.Instant;
import java.util.Base64;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Fetches and caches the Ed25519 public key used to verify JWTs issued by the Get401 service.
 *
 * <p>The key is retrieved from the remote {@code /v1/apps/auth/public-key} endpoint on first use
 * and held in memory until its {@code expires_at} time is reached, at which point the next call
 * transparently re-fetches a fresh key.
 *
 * <p>All public methods on this class are thread-safe.
 */
public class JwtPublicKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtPublicKeyProvider.class);

    private static final String HEADER_APP_ID = "X-App-Id";
    private static final String PUBLIC_KEY_PATH = "/v1/apps/auth/public-key";
    private static final String DEFAULT_BASE_URL = "https://app.get401.com";

    private final String appId;
    private final String origin;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    private PublicKey cachedPublicKey;
    private Instant cachedKeyExpiresAt;

    /**
     * Creates a new provider for the given application.
     *
     * @param appId   the application ID sent in the {@code X-App-Id} request header
     * @param origin  the origin sent in the {@code Origin} request header (e.g. {@code "https://myapp.com"})
     * @param baseUrl base URL of the Get401 service, or {@code null} to use the default ({@code https://app.get401.com})
     */
    public JwtPublicKeyProvider(String appId, String origin, String baseUrl) {
        this.appId = appId;
        this.origin = origin;
        this.baseUrl = baseUrl != null ? baseUrl : DEFAULT_BASE_URL;
        this.httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
        this.mapper = new ObjectMapper();
    }

    /**
     * Returns the current Ed25519 public key used to verify JWT signatures.
     *
     * <p>The key is fetched from the remote endpoint on first use and cached until
     * its {@code expires_at} time is reached. Once expired, the next call transparently
     * re-fetches and caches the new key.
     *
     * @return the current {@link PublicKey} for JWT verification
     * @throws RuntimeException if the key cannot be fetched or parsed
     */
    public synchronized PublicKey getPublicKey() {
        if (isCachedKeyValid()) {
            return cachedPublicKey;
        }

        log.debug("Fetching public key from {}", this.baseUrl);
        PublicKeyResponse keyResponse = fetchPublicKeyResponse();
        cachedPublicKey = parsePublicKey(keyResponse.base64Key);
        cachedKeyExpiresAt = Instant.ofEpochSecond(keyResponse.expiresAt);
        log.debug("Public key cached, expires at {}", cachedKeyExpiresAt);

        return cachedPublicKey;
    }

    /**
     * Returns {@code true} if a cached key exists and has not yet expired.
     */
    private boolean isCachedKeyValid() {
        return cachedPublicKey != null
                && cachedKeyExpiresAt != null
                && Instant.now().isBefore(cachedKeyExpiresAt);
    }

    /**
     * Sends an HTTP GET request to the public-key endpoint and returns the
     * parsed {@link PublicKeyResponse}.
     *
     * @return the response containing the raw base64 key and its expiry
     * @throws RuntimeException if the HTTP request fails or the response is empty/malformed
     */
    private PublicKeyResponse fetchPublicKeyResponse() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(this.baseUrl + PUBLIC_KEY_PATH))
                    .header(HEADER_APP_ID, this.appId)
                    .header("Origin", this.origin)
                    .GET()
                    .build();

            HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            String body = response.body();
            if (body == null || body.isBlank()) {
                throw new RuntimeException("Empty response body from public-key endpoint");
            }

            return parsePublicKeyResponse(body);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch public key from " + this.baseUrl, e);
        }
    }

    /**
     * Parses the JSON response body from the public-key endpoint.
     *
     * @param responseBody the raw JSON string
     * @return a {@link PublicKeyResponse} with the base64-encoded key and Unix expiry timestamp
     * @throws RuntimeException if the JSON cannot be parsed or required fields are missing
     */
    private PublicKeyResponse parsePublicKeyResponse(String responseBody) {
        try {
            JsonNode root = mapper.readTree(responseBody);
            String base64Key = root.get("public_key").asText();
            long expiresAt = root.get("expires_at").asLong();
            return new PublicKeyResponse(base64Key, expiresAt);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse public-key response JSON", e);
        }
    }

    /**
     * Decodes a Base64-encoded Ed25519 public key into a {@link PublicKey} instance.
     *
     * @param base64 the Base64-encoded X.509 public key bytes
     * @return the decoded {@link PublicKey}
     * @throws RuntimeException if decoding or key generation fails
     */
    private PublicKey parsePublicKey(String base64) {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
            return KeyFactory.getInstance("Ed25519").generatePublic(keySpec);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Ed25519 public key", e);
        }
    }

    /**
     * Holds the raw data returned by the public-key endpoint.
     */
    private static class PublicKeyResponse {
        final String base64Key;
        final long expiresAt;

        PublicKeyResponse(String base64Key, long expiresAt) {
            this.base64Key = base64Key;
            this.expiresAt = expiresAt;
        }
    }
}
