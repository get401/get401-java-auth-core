package com.get401.auth.core.client;

/**
 * Thrown when the Get401 API returns a non-2xx HTTP status code.
 *
 * <p>The {@link #getStatus()} and {@link #getErrorCode()} values map directly to
 * the API error response documented in BE_API.md:
 * <ul>
 *   <li>{@code 401} / {@code unauthorized} — missing, invalid, or expired API key</li>
 *   <li>{@code 404} / {@code not_found} — user does not exist or belongs to another tenant</li>
 *   <li>{@code 500} / {@code internal} — unexpected server error</li>
 * </ul>
 */
public class Get401ApiException extends RuntimeException {

    /** The HTTP status code returned by the server (e.g. {@code 401}, {@code 404}, {@code 500}). */
    private final int status;

    /** The error code extracted from the response body (e.g. {@code "unauthorized"}, {@code "not_found"}). */
    private final String errorCode;

    /**
     * Creates a new exception representing a failed API operation.
     *
     * @param operation a short description of the operation that failed (e.g. {@code "GET user usr_abc123"})
     * @param status    the HTTP status code returned by the server
     * @param errorCode the {@code "error"} field from the response body, or a fallback description
     */
    public Get401ApiException(String operation, int status, String errorCode) {
        super(String.format("%s failed — HTTP %d: %s", operation, status, errorCode));
        this.status = status;
        this.errorCode = errorCode;
    }

    /**
     * Returns the HTTP status code returned by the server.
     *
     * @return HTTP status code (e.g. {@code 401}, {@code 404}, {@code 500})
     */
    public int getStatus() {
        return status;
    }

    /**
     * Returns the error code extracted from the {@code "error"} field of the response body.
     *
     * @return error code string (e.g. {@code "unauthorized"}, {@code "not_found"}, {@code "internal"})
     */
    public String getErrorCode() {
        return errorCode;
    }
}
