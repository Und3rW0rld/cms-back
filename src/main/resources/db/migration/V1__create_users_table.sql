-- V1__create_users_table.sql
-- Full normalized user identity schema
-- Supports local auth, OAuth2, multiple roles per user, and flexible profile data

CREATE TABLE IF NOT EXISTS users (
    id         BIGSERIAL    PRIMARY KEY,
    email      VARCHAR(100) NOT NULL UNIQUE,
    name       VARCHAR(100) NOT NULL,
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    plan_id    UUID         NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS roles (
    id   SERIAL      PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

INSERT INTO roles (name) VALUES ('ADMIN'), ('EDITOR')
    ON CONFLICT (name) DO NOTHING;

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id),
    role_id INT    NOT NULL REFERENCES roles(id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS user_credentials (
    user_id       BIGINT       PRIMARY KEY REFERENCES users(id),
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS user_oauth_providers (
    id               BIGSERIAL   PRIMARY KEY,
    user_id          BIGINT      NOT NULL REFERENCES users(id),
    provider         VARCHAR(30) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    created_at       TIMESTAMP   NOT NULL DEFAULT NOW(),
    UNIQUE (provider, provider_user_id)
);

CREATE TABLE IF NOT EXISTS user_profiles (
    user_id    BIGINT        PRIMARY KEY REFERENCES users(id),
    last_name  VARCHAR(100)  NULL,
    phone      VARCHAR(30)   NULL,
    bio        TEXT          NULL,
    avatar_url VARCHAR(500)  NULL,
    website    VARCHAR(255)  NULL,
    metadata   JSONB         NULL DEFAULT '{}',
    updated_at TIMESTAMP     NOT NULL DEFAULT NOW()
);
