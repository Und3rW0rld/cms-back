package com.cms.adapters.out.persistence.jpa.adapter;

final class SiteSqlColumns {

    private SiteSqlColumns() {
        throw new AssertionError("Utility class - cannot be instantiated");
    }

    // ===== sites table =====
    static final String ID = "id";
    static final String OWNER_USER_ID = "owner_user_id";
    static final String TITLE = "title";
    static final String SUMMARY = "summary";
    static final String CONTENT_SCHEMA = "content_schema";
    static final String CREATED_AT = "created_at";
    static final String UPDATED_AT = "updated_at";

    // ===== site_published table (FK column, distinct from sites.id) =====
    static final String SITE_ID = "site_id";

    // ===== derived column (LEFT JOIN against site_published) =====
    static final String IS_PUBLISHED = "is_published";
}
