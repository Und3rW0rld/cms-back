package com.cms.adapters.out.persistence.jpa.adapter;

final class PublishedSiteColumns {
    private PublishedSiteColumns(){
        throw new AssertionError("Utility class - cannot be instantiated");
    }
    public static final String SITE_ID = "site_id";
    public static final String CONTENT = "content";
    public static final String PUBLISHED_AT = "published_at";
}
