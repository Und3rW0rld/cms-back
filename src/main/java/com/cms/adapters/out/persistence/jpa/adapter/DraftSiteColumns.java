package com.cms.adapters.out.persistence.jpa.adapter;

final class DraftSiteColumns {

    private DraftSiteColumns() {
        throw new AssertionError("Utility class - cannot be instantiated");
    }

    public static final String SITE_ID = "site_id";
    public static final String VERSION = "version";
    public static final String CONTENT = "content";
    public static final String UPDATED_AT = "updated_at";

}
