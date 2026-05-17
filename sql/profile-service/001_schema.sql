PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS registration_templates (
  template_id TEXT PRIMARY KEY,
  template_name TEXT NOT NULL UNIQUE,
  enabled INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
