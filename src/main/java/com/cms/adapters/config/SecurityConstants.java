package com.cms.adapters.config;

/**
 * Security-related constants used across the application.
 * Centralized to avoid magic strings and ensure consistency.
 *
 * Role names live in {@code com.cms.domain.model.user.Role} (the domain enum) —
 * this class only holds the Spring Security-specific "ROLE_" prefix convention,
 * to avoid defining role names in two places.
 */
public class SecurityConstants {

    private SecurityConstants() {
        throw new AssertionError("Utility class - cannot be instantiated");
    }

    // JWT & Authentication
    public static final String AUTH_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String BEARER_SCHEME = "bearer";
    public static final String JWT_FORMAT = "JWT";
    public static final String SECURITY_SCHEME_NAME = "bearerAuth";

    // Spring Security authority prefix convention — see com.cms.domain.model.user.Role for role names
    public static final String ROLE_PREFIX = "ROLE_";

    // API Paths
    public static final String AUTH_ENDPOINT = "/auth/**";
    public static final String CMS_ENDPOINT = "/cms/**";
    public static final String PUBLIC_ENDPOINT = "/public/**";
    public static final String ADMIN_ENDPOINT = "/admin/**";
    public static final String ACTUATOR_ENDPOINT = "/actuator/**";

    // OpenAPI Tags
    public static final String TAG_AUTH = "auth";
    public static final String TAG_CMS_SITES = "cms-sites";
    public static final String TAG_CMS_ENTRIES = "cms-entries";
    public static final String TAG_PUBLIC = "public";
    public static final String TAG_ADMIN = "admin";

}
