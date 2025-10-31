-- ============================================================================
-- Splatcast - Initialization Schema (PostgreSQL 13+)
-- Surrogate PKs + composite (app_id, name) business keys
-- ============================================================================

BEGIN;

-- =========================
-- 0) Enums
-- =========================
CREATE TYPE api_key_role   AS ENUM ('admin','publisher','subscriber');
CREATE TYPE schema_status  AS ENUM ('draft','active','deprecated');
CREATE TYPE transform_lang AS ENUM ('JS');
CREATE TYPE acl_action     AS ENUM ('publish','subscribe');

-- =========================
-- 1) Apps (tenants)
-- =========================
CREATE TABLE apps (
  id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name       TEXT NOT NULL UNIQUE,               -- globally unique app name
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =========================
-- 2) API keys (store only hash)
-- =========================
CREATE TABLE api_keys (
  id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  app_id       BIGINT NOT NULL REFERENCES apps(id) ON DELETE CASCADE,
  key_hash     TEXT   NOT NULL,                  -- sha256/argon2 hash of plaintext key
  role         api_key_role NOT NULL DEFAULT 'publisher',
  label        TEXT,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_used_at TIMESTAMPTZ
);
-- Prevent dup keys per app regardless of label
CREATE UNIQUE INDEX uq_api_keys_app_hash ON api_keys(app_id, key_hash);

-- =========================
-- 3) Schemas (per app, named)
-- =========================
CREATE TABLE schemas (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  app_id      BIGINT NOT NULL REFERENCES apps(id) ON DELETE CASCADE,
  name        TEXT   NOT NULL,                   -- human-friendly label
  json_schema JSONB  NOT NULL,                   -- JSON Schema document
  status      schema_status NOT NULL DEFAULT 'active',
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uniq_schema_per_app UNIQUE (app_id, name)
);

-- =========================
-- 4) Topics (per app, named; can reference a default schema)
-- =========================
CREATE TABLE topics (
  id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  app_id            BIGINT NOT NULL REFERENCES apps(id) ON DELETE CASCADE,
  name              TEXT   NOT NULL,             -- display name; unique per app
  description       TEXT,
  retention_hours   INT    NOT NULL DEFAULT 72,  -- hint for stream retention
  default_schema_id BIGINT REFERENCES schemas(id) ON DELETE SET NULL,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_topics_retention_pos CHECK (retention_hours > 0),
  CONSTRAINT uniq_topic_per_app UNIQUE (app_id, name)
);

-- Guardrail: topic.default_schema_id must belong to the same app as the topic
CREATE OR REPLACE FUNCTION trg_topics_default_schema_app_guard()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE s_app BIGINT;
BEGIN
  IF NEW.default_schema_id IS NULL THEN
    RETURN NEW;
  END IF;

  SELECT app_id INTO s_app FROM schemas WHERE id = NEW.default_schema_id;
  IF s_app IS NULL OR s_app <> NEW.app_id THEN
    RAISE EXCEPTION 'default_schema_id % belongs to app %, but topic % is app %',
      NEW.default_schema_id, COALESCE(s_app::TEXT,'<unknown>'), NEW.id, NEW.app_id;
  END IF;
  RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS topics_default_schema_app_guard ON topics;
CREATE TRIGGER topics_default_schema_app_guard
BEFORE INSERT OR UPDATE OF default_schema_id, app_id ON topics
FOR EACH ROW EXECUTE FUNCTION trg_topics_default_schema_app_guard();

-- =========================
-- 5) Transformers (per app, named) — operate on topics/schemas
-- =========================
CREATE TABLE transformers (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  app_id      BIGINT NOT NULL REFERENCES apps(id) ON DELETE CASCADE,
  name        TEXT   NOT NULL,                   -- business name (unique per app)
  topic_id    BIGINT NOT NULL REFERENCES topics(id) ON DELETE CASCADE,
  from_schema BIGINT REFERENCES schemas(id) ON DELETE SET NULL, -- NULL = any/wildcard
  to_schema   BIGINT REFERENCES schemas(id) ON DELETE SET NULL, -- target schema
  lang        transform_lang NOT NULL DEFAULT 'JS',
  code        TEXT   NOT NULL,                   -- human-readable JS source
  code_hash   TEXT   NOT NULL,                   -- sha256('JS|' || code)
  timeout_ms  INT    NOT NULL DEFAULT 50,
  enabled     BOOLEAN NOT NULL DEFAULT TRUE,
  created_by  TEXT,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_js_timeout_range CHECK (timeout_ms BETWEEN 1 AND 5000),
  CONSTRAINT uniq_transformer_per_app UNIQUE (app_id, name)
);

-- Ensure cross-table app consistency: transformer, its topic, and schemas share the same app
CREATE OR REPLACE FUNCTION trg_transformers_app_guard()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE t_app BIGINT;
DECLARE fs_app BIGINT;
DECLARE ts_app BIGINT;
BEGIN
  -- Topic must exist and match transformer.app_id
  SELECT app_id INTO t_app FROM topics WHERE id = NEW.topic_id;
  IF t_app IS NULL OR t_app <> NEW.app_id THEN
    RAISE EXCEPTION 'transformer %. topic % belongs to app %, but transformer.app_id is %',
      NEW.name, NEW.topic_id, COALESCE(t_app::TEXT,'<unknown>'), NEW.app_id;
  END IF;

  -- If from_schema set, it must match transformer.app_id
  IF NEW.from_schema IS NOT NULL THEN
    SELECT app_id INTO fs_app FROM schemas WHERE id = NEW.from_schema;
    IF fs_app IS NULL OR fs_app <> NEW.app_id THEN
      RAISE EXCEPTION 'from_schema % belongs to app %, but transformer.app_id is %',
        NEW.from_schema, COALESCE(fs_app::TEXT,'<unknown>'), NEW.app_id;
    END IF;
  END IF;

  -- If to_schema set, it must match transformer.app_id
  IF NEW.to_schema IS NOT NULL THEN
    SELECT app_id INTO ts_app FROM schemas WHERE id = NEW.to_schema;
    IF ts_app IS NULL OR ts_app <> NEW.app_id THEN
      RAISE EXCEPTION 'to_schema % belongs to app %, but transformer.app_id is %',
        NEW.to_schema, COALESCE(ts_app::TEXT,'<unknown>'), NEW.app_id;
    END IF;
  END IF;

  RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS transformers_app_guard ON transformers;
CREATE TRIGGER transformers_app_guard
BEFORE INSERT OR UPDATE OF app_id, topic_id, from_schema, to_schema ON transformers
FOR EACH ROW EXECUTE FUNCTION trg_transformers_app_guard();

-- Helpful lookups for routing/pipelines
CREATE UNIQUE INDEX uq_transformers_active
  ON transformers (app_id, topic_id, COALESCE(from_schema, -1), to_schema, code_hash)
  WHERE enabled;

CREATE INDEX ix_transformers_lookup
  ON transformers (app_id, topic_id, COALESCE(from_schema, -1), to_schema)
  WHERE enabled;

CREATE INDEX ix_transformers_topic_to_from_enabled
  ON transformers (topic_id, to_schema, from_schema)
  WHERE enabled;

-- =========================
-- 6) Transformer test vectors (optional)
-- =========================
CREATE TABLE transformer_tests (
  id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  transformer_id BIGINT NOT NULL REFERENCES transformers(id) ON DELETE CASCADE,
  input_json     JSONB  NOT NULL,
  expect_json    JSONB  NOT NULL,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =========================
-- 7) ACLs (per topic & key)
-- =========================
CREATE TABLE acls (
  id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  app_id     BIGINT NOT NULL REFERENCES apps(id) ON DELETE CASCADE,
  topic_id   BIGINT NOT NULL REFERENCES topics(id) ON DELETE CASCADE,
  api_key_id BIGINT NOT NULL REFERENCES api_keys(id) ON DELETE CASCADE,
  action     acl_action NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (app_id, topic_id, api_key_id, action)
);

-- =========================
-- 8) Quotas / rate limits
-- =========================
CREATE TABLE quotas (
  id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  app_id     BIGINT NOT NULL REFERENCES apps(id) ON DELETE CASCADE,
  topic_id   BIGINT NOT NULL REFERENCES topics(id) ON DELETE CASCADE,
  per_minute INT NOT NULL DEFAULT 6000,
  per_day    INT NOT NULL DEFAULT 1000000,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_quota_nonneg CHECK (per_minute > 0 AND per_day > 0),
  UNIQUE (app_id, topic_id)
);

-- =========================
-- 9) Audit log
-- =========================
CREATE TABLE audit_events (
  id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  app_id  BIGINT NOT NULL REFERENCES apps(id) ON DELETE CASCADE,
  actor   TEXT NOT NULL,                      -- api key label / user id
  action  TEXT NOT NULL,                      -- e.g., CREATE_TRANSFORMER
  target  TEXT NOT NULL,                      -- e.g., "transformers:42"
  details JSONB,
  at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_app_at ON audit_events(app_id, at DESC);

-- =========================
-- 10) Messaging (if/when you store messages)
-- =========================
-- Example table demonstrating good indexing for topic reads.
-- Uncomment if needed.
-- CREATE TABLE messages (
--   id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
--   topic_id     BIGINT NOT NULL REFERENCES topics(id) ON DELETE CASCADE,
--   published_at TIMESTAMPTZ NOT NULL DEFAULT now(),
--   payload      JSONB  NOT NULL,
--   meta         JSONB,
--   CHECK (payload IS NOT NULL)
-- );
-- CREATE INDEX idx_messages_topic_time ON messages(topic_id, published_at);

COMMIT;
