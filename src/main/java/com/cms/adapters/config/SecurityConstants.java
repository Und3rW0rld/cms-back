package com.cms.adapters.config;

/**
 * Security-related constants used across the application.
 * Centralized to avoid magic strings and ensure consistency.
 *
 * Note: Role enum names (ADMIN, EDITOR, VIEWER) are stored in database.
 * When converted to GrantedAuthority, they become "ROLE_ADMIN", "ROLE_EDITOR", "ROLE_VIEWER"
 * (as per UserEntity.getAuthorities()).
 *
 * Use the AUTHORITY_* constants (with ROLE_ prefix) for @PreAuthorize and security checks.
 * Use the ROLE_* constants (bare names) only when mapping to/from the database.
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

    // Roles — stored as enum names in database
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_EDITOR = "EDITOR";
    public static final String ROLE_VIEWER = "VIEWER";
    public static final String ROLE_PREFIX = "ROLE_";

    // Granted Authorities — used in @PreAuthorize and security checks
    // These match the values returned by UserEntity.getAuthorities()
    public static final String AUTHORITY_ADMIN = ROLE_PREFIX + ROLE_ADMIN;      // "ROLE_ADMIN"
    public static final String AUTHORITY_EDITOR = ROLE_PREFIX + ROLE_EDITOR;    // "ROLE_EDITOR"
    public static final String AUTHORITY_VIEWER = ROLE_PREFIX + ROLE_VIEWER;    // "ROLE_VIEWER"

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
