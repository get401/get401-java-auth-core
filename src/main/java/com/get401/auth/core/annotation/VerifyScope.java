package com.get401.auth.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies the scopes required to access the annotated method or class.
 * The user's JWT 'scope' string (comma-separated) must contain all the
 * specified scopes, or at least one, depending on implementation rules.
 * Verify that ALL specified scopes in the annotation are present in the
 * token's scope string.
 * A verified JWT token is required (implies @AuthGet401 behavior).
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface VerifyScope {
    String[] value();
}
