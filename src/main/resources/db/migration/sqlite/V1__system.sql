-- TDS system tables (architecture §6.1). Keep in step with db/migration/postgresql/V1__system.sql.

CREATE TABLE tds_user (
  id                   INTEGER PRIMARY KEY AUTOINCREMENT,
  username             TEXT NOT NULL UNIQUE CHECK (length(username) BETWEEN 1 AND 100),
  password_hash        TEXT NOT NULL,
  display_name         TEXT CHECK (length(display_name) <= 200),
  enabled              INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0, 1)),
  must_change_password INTEGER NOT NULL DEFAULT 0 CHECK (must_change_password IN (0, 1)),
  created_at           TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
  updated_at           TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

CREATE TABLE tds_role (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  name        TEXT NOT NULL UNIQUE CHECK (length(name) BETWEEN 1 AND 41),
  description TEXT CHECK (length(description) <= 500),
  builtin     INTEGER NOT NULL DEFAULT 0 CHECK (builtin IN (0, 1))
);

CREATE TABLE tds_user_role (
  user_id INTEGER NOT NULL REFERENCES tds_user(id) ON DELETE CASCADE,
  role_id INTEGER NOT NULL REFERENCES tds_role(id) ON DELETE RESTRICT,
  PRIMARY KEY (user_id, role_id)
);
CREATE INDEX ix_tds_user_role_role ON tds_user_role (role_id);

CREATE TABLE tds_template (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  name              TEXT NOT NULL UNIQUE CHECK (length(name) BETWEEN 1 AND 63),
  manage_type       TEXT NOT NULL CHECK (manage_type IN ('VIEW', 'MANAGE_VIEW', 'DATA_SOURCE')),
  draft_json        TEXT NOT NULL,
  draft_checksum    TEXT NOT NULL,
  published_version INTEGER,
  updated_at        TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
  updated_by        TEXT NOT NULL
);

CREATE TABLE tds_template_version (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  template_id   INTEGER NOT NULL REFERENCES tds_template(id) ON DELETE CASCADE,
  version       INTEGER NOT NULL CHECK (version > 0),
  template_json TEXT NOT NULL,
  checksum      TEXT NOT NULL,
  plan_json     TEXT,
  applied_sql   TEXT,
  published_at  TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
  published_by  TEXT NOT NULL,
  UNIQUE (template_id, version)
);

CREATE TABLE tds_action_log (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  template_name TEXT NOT NULL,
  action_name   TEXT NOT NULL,
  record_id     INTEGER,
  username      TEXT NOT NULL,
  started_at    TEXT NOT NULL,
  duration_ms   INTEGER NOT NULL,
  status        TEXT NOT NULL CHECK (status IN ('OK', 'ERROR', 'TIMEOUT')),
  message       TEXT
);
CREATE INDEX ix_tds_action_log_started ON tds_action_log (started_at);

INSERT INTO tds_role (name, description, builtin) VALUES
  ('admin',  'Full access. Manages templates, roles and users.', 1),
  ('viewer', 'Default role held by every user.', 1);
