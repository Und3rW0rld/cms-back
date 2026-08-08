-- V2__create_sites_schema.sql
-- Sites table — publishable content root unit

CREATE TABLE IF NOT EXISTS sites (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id  BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title          VARCHAR(150) NOT NULL,
    summary        VARCHAR(255) NULL,
    content_schema VARCHAR(100) NULL,                   -- e.g., "portfolio-v1" for frontend versioning
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- No status column. Published state is determined by presence in site_published table.

CREATE INDEX idx_sites_owner_user_id ON sites (owner_user_id);
CREATE INDEX idx_sites_created_at ON sites (created_at DESC);
