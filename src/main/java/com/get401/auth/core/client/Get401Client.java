package com.get401.auth.core.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.get401.auth.core.model.User;
import com.get401.auth.core.model.UsersPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Client for the Get401 backend (server-to-server) API.
 *
 * <p>All requests are authenticated with an API key passed as a Bearer token.
 * Every user operation is automatically scoped to the tenant that owns the key —
 * it is not possible to access users from another tenant.
 *
 * <pre>{@code
 * Get401Client client = new Get401Client("sk_live_your_key");
 *
 * UsersPage page = client.listUsers(20);
 * User user = client.getUserById("usr_abc123");
 * client.disableUser("usr_abc123");
 * }</pre>
 */
public class Get401Client {

    private static final Logger log = LoggerFactory.getLogger(Get401Client.class);

    private static final String DEFAULT_BASE_URL = "https://app.get401.com";
    private static final String USERS_PATH = "/v1/apps/users";

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    /**
     * Creates a client using the default Get401 base URL ({@code https://app.get401.com}).
     *
     * @param apiKey the API key ({@code sk_live_...}) used to authenticate all requests
     */
    public Get401Client(String apiKey) {
        this(apiKey, null);
    }

    /**
     * Creates a client with a custom base URL, useful for testing against a local or staging instance.
     *
     * @param apiKey  the API key ({@code sk_live_...}) used to authenticate all requests
     * @param baseUrl base URL of the Get401 service, or {@code null} to use the default
     */
    public Get401Client(String apiKey, String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl != null ? baseUrl : DEFAULT_BASE_URL;
        this.httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
        this.mapper = new ObjectMapper();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the first page of users for this tenant using the server's default page size.
     *
     * @return the first {@link UsersPage}
     * @throws Get401ApiException if the server returns an error response
     * @throws RuntimeException   if the request fails due to a network or parsing error
     */
    public UsersPage listUsers() {
        return listUsers(null, null);
    }

    /**
     * Returns the first page of users for this tenant with a specific page size.
     *
     * @param pageSize number of users per page (1–100)
     * @return the first {@link UsersPage}
     * @throws Get401ApiException if the server returns an error response
     * @throws RuntimeException   if the request fails due to a network or parsing error
     */
    public UsersPage listUsers(int pageSize) {
        return listUsers(pageSize, null);
    }

    /**
     * Returns the next page of users identified by an opaque cursor.
     *
     * <p>Obtain the cursor from {@code UsersPage.getNext()} of a previous response.
     * When {@code getNext()} returns {@code null}, there are no more pages.
     *
     * @param cursor the opaque pagination cursor from the previous {@code UsersPage.getNext()}
     * @return the next {@link UsersPage}
     * @throws Get401ApiException if the server returns an error response
     * @throws RuntimeException   if the request fails due to a network or parsing error
     */
    public UsersPage listUsers(String cursor) {
        return listUsers(null, cursor);
    }

    /**
     * Returns a single user by their public ID, scoped to this tenant.
     *
     * @param id public user ID (e.g. {@code usr_abc123})
     * @return the matching {@link User}
     * @throws Get401ApiException if the user is not found ({@code 404}) or the key is invalid ({@code 401})
     * @throws RuntimeException   if the request fails due to a network or parsing error
     */
    public User getUserById(String id) {
        log.debug("Fetching user {}", id);

        HttpRequest request = newAuthorizedRequest(URI.create(baseUrl + USERS_PATH + "/" + id))
                .GET()
                .build();

        HttpResponse<String> response = send(request);
        requireSuccessStatus(response, "GET user " + id);

        return deserialize(response.body(), User.class);
    }

    /**
     * Disables a user, preventing them from authenticating.
     *
     * <p>This operation sets the user's {@code is_active} flag to {@code false}.
     * The user record is retained and can be re-enabled via the platform UI.
     *
     * @param id public user ID (e.g. {@code usr_abc123})
     * @throws Get401ApiException if the user is not found ({@code 404}) or the key is invalid ({@code 401})
     * @throws RuntimeException   if the request fails due to a network or parsing error
     */
    public void disableUser(String id) {
        log.debug("Disabling user {}", id);

        HttpRequest request = newAuthorizedRequest(URI.create(baseUrl + USERS_PATH + "/" + id))
                .DELETE()
                .build();

        HttpResponse<String> response = send(request);
        requireSuccessStatus(response, "DELETE user " + id);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Core list-users implementation shared by all public overloads.
     * Exactly one of {@code pageSize} or {@code cursor} should be non-null on any given call.
     * When both are {@code null}, the server uses its default page size.
     *
     * @param pageSize number of items per page, or {@code null} to use the server default
     * @param cursor   opaque pagination cursor, or {@code null} to start from the first page
     */
    private UsersPage listUsers(Integer pageSize, String cursor) {
        URI uri = buildListUsersUri(pageSize, cursor);
        log.debug("Listing users: {}", uri);

        HttpRequest request = newAuthorizedRequest(uri).GET().build();
        HttpResponse<String> response = send(request);
        requireSuccessStatus(response, "GET users");

        return deserialize(response.body(), UsersPage.class);
    }

    /**
     * Builds the URI for the list-users endpoint, appending query parameters as needed.
     *
     * @param pageSize number of items per page, appended as {@code ?n=} when non-null
     * @param cursor   pagination cursor, appended as {@code ?c=} when non-null (takes precedence over pageSize)
     * @return the fully-formed URI
     */
    private URI buildListUsersUri(Integer pageSize, String cursor) {
        StringBuilder url = new StringBuilder(baseUrl).append(USERS_PATH);
        if (cursor != null) {
            url.append("?c=").append(cursor);
        } else if (pageSize != null) {
            url.append("?n=").append(pageSize);
        }
        return URI.create(url.toString());
    }

    /**
     * Returns a pre-configured {@link HttpRequest.Builder} with the {@code Authorization} header set.
     *
     * @param uri the target URI
     * @return a builder ready for a specific HTTP method
     */
    private HttpRequest.Builder newAuthorizedRequest(URI uri) {
        return HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer " + apiKey);
    }

    /**
     * Sends the given request synchronously and returns the response.
     *
     * @param request the request to send
     * @return the HTTP response
     * @throws RuntimeException if an I/O or interruption error occurs
     */
    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new RuntimeException("HTTP request failed: " + request.uri(), e);
        }
    }

    /**
     * Throws {@link Get401ApiException} if the response status code indicates an error.
     * The exception message includes the error code from the response body when available.
     *
     * @param response  the HTTP response to inspect
     * @param operation a short description of the operation (used in error messages)
     * @throws Get401ApiException if the status code is not in the 2xx range
     */
    private void requireSuccessStatus(HttpResponse<String> response, String operation) {
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return;
        }

        String errorCode = extractErrorCode(response.body());
        throw new Get401ApiException(operation, status, errorCode);
    }

    /**
     * Attempts to extract the {@code "error"} field from a JSON error response body.
     * Returns the raw body string if parsing fails.
     *
     * @param body the response body
     * @return the error code string, or the raw body as fallback
     */
    private String extractErrorCode(String body) {
        if (body == null || body.isBlank()) {
            return "(empty response)";
        }
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode errorNode = root.get("error");
            return errorNode != null ? errorNode.asText() : body;
        } catch (Exception ignored) {
            return body;
        }
    }

    /**
     * Deserializes a JSON string into an instance of the given class.
     *
     * @param json        the JSON string to parse
     * @param targetClass the target type
     * @param <T>         the target type parameter
     * @return the deserialized object
     * @throws RuntimeException if deserialization fails
     */
    private <T> T deserialize(String json, Class<T> targetClass) {
        try {
            return mapper.readValue(json, targetClass);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize response into " + targetClass.getSimpleName(), e);
        }
    }
}
