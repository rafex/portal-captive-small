INSERT INTO users (id, password_hash, password_salt, created_at, updated_at) VALUES (
  '00000000-0000-0000-0000-000000000001',
  'CHANGE_ME_HASH',
  'CHANGE_ME_SALT',
  datetime('now'),
  datetime('now')
);

INSERT INTO user_profiles (user_id, profile_json, created_at, updated_at) VALUES (
  '00000000-0000-0000-0000-000000000001',
  '{"firstName":"Admin","lastName":"Portal","age":30,"email":"admin@example.com","phone":"+5210000000000","mobile":"+5210000000001","address":"N/A","socialFacebook":null,"socialInstagram":null,"socialTiktok":null,"socialX":null}',
  datetime('now'),
  datetime('now')
);
