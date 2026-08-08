-- V3__create_site_entries_schema.sql
-- Site entries table — individual content items within a site
-- Uses ltree for hierarchical structure (series, posts, nested content)

-- ============================================================
-- ltree extension — required for hierarchical queries
-- ============================================================
CREATE EXTENSION IF NOT EXISTS ltree;

-- ============================================================
-- Site entries table
-- ============================================================
CREATE TABLE IF NOT EXISTS site_entries (
    id        UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    site_id   UUID        NOT NULL REFERENCES sites (id) ON DELETE CASCADE,
    path      LTREE       NOT NULL,                      -- e.g., "root.seriesId.entryId"
    type      VARCHAR(50) NOT NULL,                      -- "post", "project", "series" (not validated)
    sort_order INT        NULL,                          -- optional sort order within parent
    created_at TIMESTAMP  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP  NOT NULL DEFAULT NOW(),
    UNIQUE (site_id, path)                               -- Ensure unique paths within a site
);

-- No status column. Published state is determined by presence in site_entry_published table.

-- ============================================================
-- Indexes for hierarchical queries
-- ============================================================
-- GiST index for ltree operators (<@, @>, etc.)
CREATE INDEX idx_site_entries_path_gist ON site_entries USING GIST (path);

-- B-tree indexes for common queries
CREATE INDEX idx_site_entries_site_path ON site_entries (site_id, path);
CREATE INDEX idx_site_entries_site_type ON site_entries (site_id, type);
CREATE INDEX idx_site_entries_created_at ON site_entries (created_at DESC);
