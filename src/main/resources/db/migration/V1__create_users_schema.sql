-- V1__create_users_schema.sql
-- User identity and authentication schema
-- Normalized tables following domain design

-- ============================================================
-- pgcrypto extension — required for gen_random_uuid()
-- ============================================================
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ============================================================
-- Roles table — reference data
-- ============================================================
CREATE TABLE IF NOT EXISTS roles (
    id         SERIAL PRIMARY KEY,
    name       VARCHAR(50) NOT NULL UNIQUE,
    created_at TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- Seed roles with ADMIN and EDITOR (VIEWER role deferred)
INSERT INTO roles (name) VALUES ('ADMIN'), ('EDITOR') 
    ON CONFLICT (name) DO NOTHING;

-- ============================================================
-- Users table — core identity
-- ============================================================
CREATE TABLE IF NOT EXISTS users (
    id         BIGSERIAL PRIMARY KEY,
    email      VARCHAR(100) NOT NULL UNIQUE,
    name       VARCHAR(100) NOT NULL,
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    plan_id    UUID         NULL,                      -- FK to plans (future monetization)
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- UNIQUE constraint on email automatically creates a btree index
-- No need for explicit idx_users_email

CREATE INDEX idx_users_plan_id ON users (plan_id);

-- ============================================================
-- User roles junction table — many-to-many
-- ============================================================
CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT  NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id INT     NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_user_roles_role_id ON user_roles (role_id);

-- ============================================================
-- User credentials — password storage
-- ============================================================
CREATE TABLE IF NOT EXISTS user_credentials (
    user_id       BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ============================================================
-- User OAuth providers — social login (future)
-- ============================================================
CREATE TABLE IF NOT EXISTS user_oauth_providers (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider         VARCHAR(30) NOT NULL,              -- GOOGLE, GITHUB
    provider_user_id VARCHAR(255) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (provider, provider_user_id)
);

CREATE INDEX idx_oauth_providers_user_id ON user_oauth_providers (user_id);

-- ============================================================
-- User profiles — extended profile data
-- ============================================================
CREATE TABLE IF NOT EXISTS user_profiles (
    user_id   BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    last_name VARCHAR(100) NULL,
    phone     VARCHAR(30)  NULL,
    bio       TEXT         NULL,
    avatar_url VARCHAR(500) NULL,
    website   VARCHAR(255) NULL,
    metadata  JSONB        NOT NULL DEFAULT '{}',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
