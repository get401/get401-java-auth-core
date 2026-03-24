package com.get401.auth.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A single page of {@link User} objects returned by the list-users endpoint.
 *
 * <p>Pagination example:
 * <pre>{@code
 * UsersPage page = client.listUsers(20);
 * process(page.getItems());
 *
 * while (page.getNext() != null) {
 *     page = client.listUsers(page.getNext());
 *     process(page.getItems());
 * }
 * }</pre>
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UsersPage {

    /** The users on this page. May be empty on the last page, never {@code null}. */
    private List<User> items;

    /**
     * Opaque cursor for the next page. {@code null} when this is the last page.
     *
     * <p>Pass this value to {@link com.get401.auth.core.client.Get401Client#listUsers(String)}
     * to retrieve the following page.
     */
    private String next;
}
