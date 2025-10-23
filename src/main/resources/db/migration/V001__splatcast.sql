-- V001__splatcast_text_ids_no_domain.sql
-- Postgres 13+ recommended (but no special extensions required)

-- =========================
-- 1) Apps (tenants)
-- =========================
CREATE TABLE apps (
  app_id     text PRIMARY KEY,                 -- e.g., "acme", "app_01H..."
  name       text NOT NULL UNIQUE,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

-- =========================
-- 2) API keys (store only hash)
-- =========================
CREATE TYPE api_key_role AS ENUM ('admin','publisher','subscriber');

CREATE TABLE api_keys (
  id           text PRIMARY KEY,               -- e.g., "key_xxx"
  app_id       text NOT NULL REFERENCES apps(app_id) ON DELETE CASCADE,
  key_hash     text NOT NULL,                  -- sha256/argon2 hash of plaintext key
  role         api_key_role NOT NULL,
  label        text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  last_used_at timestamptz
);
CREATE UNIQUE INDEX uq_api_keys_app_hash ON api_keys(app_id, key_hash);

-- =========================
-- 3) Topics (per app)
-- =========================
CREATE TABLE topics (
  id                text PRIMARY KEY,          -- e.g., "orders", "topic_abc123"
  app_id            text NOT NULL REFERENCES apps(app_id) ON DELETE CASCADE,
  name              text NOT NULL,             -- display name; unique per app
  description       text,
  retention_hours   int  NOT NULL DEFAULT 72,  -- hint for stream retention
  default_schema_id text,                      -- FK to schemas.id (set after schemas exist)
  created_at        timestamptz NOT NULL DEFAULT now(),
  updated_at        timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_topics_retention_pos CHECK (retention_hours > 0),
  UNIQUE (app_id, name)
);

-- =========================
-- 4) JSON Schemas (versioned per topic)
-- =========================
CREATE TYPE schema_status AS ENUM ('draft','active','deprecated');

CREATE TABLE schemas (
  id          text PRIMARY KEY,                -- e.g., "canonical", "v2", "2025-10-18"
  app_id      text NOT NULL REFERENCES apps(app_id) ON DELETE CASCADE,
  topic_id    text NOT NULL REFERENCES topics(id) ON DELETE CASCADE,
  version     text NOT NULL,                   -- keep for clarity/search; may duplicate id
  json_schema jsonb NOT NULL,                  -- JSON Schema document
  status      schema_status NOT NULL DEFAULT 'active',
  created_at  timestamptz NOT NULL DEFAULT now(),
  UNIQUE (app_id, topic_id, version)
);

ALTER TABLE topics
  ADD CONSTRAINT fk_topics_default_schema
  FOREIGN KEY (default_schema_id) REFERENCES schemas(id) ON DELETE SET NULL;

-- =========================
-- 5) JS transforms (store readable JS + cache by hash)
-- =========================
CREATE TYPE transform_lang AS ENUM ('JS');

CREATE TABLE js_transforms (
  id          text PRIMARY KEY,                -- e.g., "trf_raw_to_canonical"
  app_id      text NOT NULL REFERENCES apps(app_id) ON DELETE CASCADE,
  topic_id    text NOT NULL REFERENCES topics(id) ON DELETE CASCADE,
  from_schema text,                            -- NULL = any
  to_schema   text NOT NULL,
  lang        transform_lang NOT NULL DEFAULT 'JS',
  code        text NOT NULL,                   -- human-readable JS source
  code_hash   text NOT NULL,                   -- sha256('JS|' || code) computed by app
  timeout_ms  int  NOT NULL DEFAULT 50,
  enabled     boolean NOT NULL DEFAULT true,
  created_by  text,
  created_at  timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_js_timeout_range CHECK (timeout_ms BETWEEN 1 AND 5000)
);

-- One active definition per (app, topic, from→to, code version)
CREATE UNIQUE INDEX uq_js_transform_active
  ON js_transforms(app_id, topic_id, coalesce(from_schema,'*'), to_schema, code_hash)
  WHERE enabled;

-- Fast lookup for building pipelines
CREATE INDEX ix_js_transform_lookup
  ON js_transforms(app_id, topic_id, coalesce(from_schema,'*'), to_schema)
  WHERE enabled;

-- Optional: test vectors to validate on upload
CREATE TABLE js_transform_tests (
  id            text PRIMARY KEY,
  transform_id  text NOT NULL REFERENCES js_transforms(id) ON DELETE CASCADE,
  input_json    jsonb NOT NULL,
  expect_json   jsonb NOT NULL,
  created_at    timestamptz NOT NULL DEFAULT now()
);

-- =========================
-- 6) ACLs (per topic & key)
-- =========================
CREATE TYPE acl_action AS ENUM ('publish','subscribe');

CREATE TABLE acls (
  id         text PRIMARY KEY,
  app_id     text NOT NULL REFERENCES apps(app_id) ON DELETE CASCADE,
  topic_id   text NOT NULL REFERENCES topics(id) ON DELETE CASCADE,
  api_key_id text NOT NULL REFERENCES api_keys(id) ON DELETE CASCADE,
  action     acl_action NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (app_id, topic_id, api_key_id, action)
);

-- =========================
-- 7) Quotas / rate limits
-- =========================
CREATE TABLE quotas (
  id         text PRIMARY KEY,
  app_id     text NOT NULL REFERENCES apps(app_id) ON DELETE CASCADE,
  topic_id   text NOT NULL REFERENCES topics(id) ON DELETE CASCADE,
  per_minute int NOT NULL DEFAULT 6000,
  per_day    int NOT NULL DEFAULT 1000000,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_quota_nonneg CHECK (per_minute > 0 AND per_day > 0),
  UNIQUE (app_id, topic_id)
);

-- =========================
-- 8) Audit log
-- =========================
CREATE TABLE audit_events (
  id        text PRIMARY KEY,
  app_id    text NOT NULL REFERENCES apps(app_id) ON DELETE CASCADE,
  actor     text NOT NULL,                     -- api key label / user id
  action    text NOT NULL,                     -- e.g., CREATE_TRANSFORM
  target    text NOT NULL,                     -- e.g., "js_transforms:trf_abc"
  details   jsonb,
  at        timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_app_at ON audit_events(app_id, at DESC);

-- =========================
-- 9) (Optional) short-lived message copies (support/compliance)
-- =========================
CREATE TABLE message_copies (
  id          text PRIMARY KEY,
  app_id      text NOT NULL REFERENCES apps(app_id) ON DELETE CASCADE,
  topic_id    text NOT NULL REFERENCES topics(id) ON DELETE CASCADE,
  schema_name text,                             -- "raw","canonical","v2", etc.
  payload     jsonb NOT NULL,
  created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_msg_copies_gc ON message_copies(created_at);
CREATE INDEX ix_msg_copies_app_topic_at ON message_copies(app_id, topic_id, created_at DESC);
