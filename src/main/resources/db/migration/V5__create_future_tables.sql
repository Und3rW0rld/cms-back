-- V5__create_future_tables.sql
-- Future tables — created now for schema completeness, no logic yet

-- ============================================================
-- Site collaborators — per-site roles (future per-site collaboration)
-- ============================================================
CREATE TABLE IF NOT EXISTS site_collaborators (
    site_id UUID    NOT NULL REFERENCES sites (id) ON DELETE CASCADE,
    user_id BIGINT  NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(30) NOT NULL,                   -- OWNER, EDITOR, AUTHOR, VIEWER
    PRIMARY KEY (site_id, user_id)
);

CREATE INDEX idx_site_collaborators_user_id ON site_collaborators (user_id);

-- ============================================================
-- Media assets — file uploads (future media management)
-- ============================================================
CREATE TABLE IF NOT EXISTS media_assets (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id  BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    site_id        UUID        NULL REFERENCES sites (id) ON DELETE CASCADE,
    storage_key    VARCHAR(255) NOT NULL UNIQUE,       -- e.g., s3://bucket/path/to/file
    public_url     VARCHAR(500) NOT NULL,              -- e.g., https://cdn.example.com/...
    alt            VARCHAR(255) NULL,                  -- Alt text for accessibility
    mime_type      VARCHAR(100) NOT NULL,              -- image/jpeg, application/pdf, etc.
    size_bytes     INT         NOT NULL,
    created_at     TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_media_assets_owner_user_id ON media_assets (owner_user_id);
CREATE INDEX idx_media_assets_site_id ON media_assets (site_id);

-- ============================================================
-- Site domains — custom domain mapping (future custom domains)
-- ============================================================
CREATE TABLE IF NOT EXISTS site_domains (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    site_id        UUID        NOT NULL REFERENCES sites (id) ON DELETE CASCADE,
    domain         VARCHAR(255) NOT NULL UNIQUE,
    verified       BOOLEAN     NOT NULL DEFAULT FALSE,
    dns_challenge  JSONB       NULL,                    -- DNS ACME challenge data
    created_at     TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_site_domains_site_id ON site_domains (site_id);

-- ============================================================
-- Site webhooks — publish/unpublish hooks (future webhooks)
-- ============================================================
CREATE TABLE IF NOT EXISTS site_webhooks (
    id        UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    site_id   UUID        NOT NULL REFERENCES sites (id) ON DELETE CASCADE,
    url       VARCHAR(500) NOT NULL,
    events    TEXT[]      NOT NULL,                     -- Array: ['publish', 'unpublish', 'delete']
    secret    VARCHAR(255) NULL,                       -- HMAC secret for webhook signatures
    created_at TIMESTAMP  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_site_webhooks_site_id ON site_webhooks (site_id);

-- ============================================================
-- Plans — pricing tiers (future SaaS monetization)
-- ============================================================
CREATE TABLE IF NOT EXISTS plans (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(50) NOT NULL UNIQUE,        -- FREE, PRO, BUSINESS
    max_sites      INT         NOT NULL,
    max_entries    INT         NOT NULL,
    max_storage_mb INT         NOT NULL,
    created_at     TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- Foreign key constraint on users.plan_id is deferred until plans are seeded
-- This will be added in a future migration after initial plan setup
