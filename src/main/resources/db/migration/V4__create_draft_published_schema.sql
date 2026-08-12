-- V4__create_draft_published_schema.sql
-- Draft and published content tables
-- Separate tables to enforce publication state through row existence

-- ============================================================
-- Site drafts — working copy with optimistic locking
-- ============================================================
CREATE TABLE IF NOT EXISTS site_drafts (
    site_id   UUID      PRIMARY KEY REFERENCES sites (id) ON DELETE CASCADE,
    version   BIGINT    NOT NULL DEFAULT 1,              -- Optimistic locking version
    content   JSONB     NOT NULL DEFAULT '{}' CHECK (jsonb_typeof(content) = 'object'),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- Site published — snapshot for public consumption
-- ============================================================
-- Existence of row = site is published. No row = draft only.
CREATE TABLE IF NOT EXISTS site_published (
    site_id    UUID      PRIMARY KEY REFERENCES sites (id) ON DELETE CASCADE,
    content    JSONB     NOT NULL CHECK (jsonb_typeof(content) = 'object'),
    published_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- Site entry drafts — working copy with optimistic locking
-- ============================================================
CREATE TABLE IF NOT EXISTS site_entry_drafts (
    entry_id  UUID      PRIMARY KEY REFERENCES site_entries (id) ON DELETE CASCADE,
    site_id   UUID      NOT NULL REFERENCES sites (id) ON DELETE CASCADE,
    version   BIGINT    NOT NULL DEFAULT 1,              -- Optimistic locking version
    content   JSONB     NOT NULL DEFAULT '{}' CHECK (jsonb_typeof(content) = 'object'),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_entry_drafts_site_id ON site_entry_drafts (site_id);

-- ============================================================
-- Site entry published — snapshot for public consumption
-- ============================================================
-- Existence of row = entry is published. No row = draft only.
CREATE TABLE IF NOT EXISTS site_entry_published (
    entry_id   UUID      PRIMARY KEY REFERENCES site_entries (id) ON DELETE CASCADE,
    site_id    UUID      NOT NULL REFERENCES sites (id) ON DELETE CASCADE,
    content    JSONB     NOT NULL CHECK (jsonb_typeof(content) = 'object'),
    published_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_entry_published_site_id ON site_entry_published (site_id);
