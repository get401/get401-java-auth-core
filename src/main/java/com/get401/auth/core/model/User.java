package com.get401.auth.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a user belonging to a Get401 tenant.
 *
 * <p>Timestamps are ISO 8601 strings in UTC (e.g. {@code "2026-01-15T08:00:00Z"}).
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class User {

    /**
     * Unique public identifier of the user (e.g. {@code usr_abc123}).
     * Stable across updates — use this value in all API calls that reference a user.
     */
    private String id;

    /** Display name of the user. */
    private String name;

    /** Email address of the user. */
    private String email;

    /**
     * Whether the user is allowed to authenticate.
     * {@code false} means the user has been disabled and cannot log in.
     */
    @JsonProperty("is_active")
    private boolean active;

    /** ISO 8601 UTC timestamp of when this user record was created. */
    @JsonProperty("created_at")
    private String createdAt;

    /** ISO 8601 UTC timestamp of when this user record was last modified. */
    @JsonProperty("updated_at")
    private String updatedAt;
}
