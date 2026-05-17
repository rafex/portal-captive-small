INSERT INTO users (
  user_id, first_name, last_name, age, email, phone, mobile, address_text,
  password_hash, password_salt, created_at, updated_at
) VALUES (
  '00000000-0000-0000-0000-000000000001',
  'Admin',
  'Portal',
  30,
  'admin@example.com',
  '+5210000000000',
  '+5210000000001',
  'N/A',
  'CHANGE_ME_HASH',
  'CHANGE_ME_SALT',
  datetime('now'),
  datetime('now')
);
