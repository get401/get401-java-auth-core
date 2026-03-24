package com.get401.auth.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies the roles required to access the annotated method or class.
 * The user's JWT must contain at least one of the specified roles.
 * A verified JWT token is required (implies @AuthGet401 behavior).
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface VerifyRoles {
    /**
     * One or more role names required to access the annotated element.
     * The JWT must contain at least one of the specified roles.
     *
     * @return the required role names
     */
    String[] value();
}
