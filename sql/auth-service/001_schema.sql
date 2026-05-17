PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS users (
  user_id TEXT PRIMARY KEY,
  first_name TEXT NOT NULL,
  last_name TEXT NOT NULL,
  age INTEGER CHECK(age >= 0),
  email TEXT UNIQUE,
  phone TEXT UNIQUE,
  mobile TEXT,
  address_text TEXT,
  osm_place_id TEXT,
  social_facebook TEXT,
  social_instagram TEXT,
  social_tiktok TEXT,
  social_x TEXT,
  password_hash TEXT NOT NULL,
  password_salt TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_phone ON users(phone);
