INSERT INTO users (username, password_hash, created_at) VALUES
  ('alice', 'hashed-pw-alice', '2026-01-01 10:00:00'),
  ('bob',   'hashed-pw-bob',   '2026-01-02 10:00:00'),
  ('carol', 'hashed-pw-carol', '2026-01-03 10:00:00');

INSERT INTO chats (chat_id, username, chat_title, messages, created_at, updated_at) VALUES
  ('01ARZ3NDEKTSV4RRFFQ69G5FA1', 'alice', 'Trip Planning',
   '[{"role":"USER","message":"Where should I go in Japan?","timestamp":"2026-01-05T09:00:00Z"},{"role":"ASSISTANT","message":"Consider Kyoto and Osaka.","timestamp":"2026-01-05T09:01:00Z"}]'::jsonb,
   '2026-01-05 09:00:00', '2026-01-05 09:01:00'),
  ('01ARZ3NDEKTSV4RRFFQ69G5FA2', 'alice', 'Recipe Ideas',
   '[{"role":"USER","message":"Suggest a pasta recipe.","timestamp":"2026-01-06T09:00:00Z"}]'::jsonb,
   '2026-01-06 09:00:00', '2026-01-06 09:00:00'),
  ('01ARZ3NDEKTSV4RRFFQ69G5FA3', 'alice', 'Same Instant A',
   '[]'::jsonb, '2026-01-07 09:00:00', '2026-01-07 09:00:00'),
  ('01ARZ3NDEKTSV4RRFFQ69G5FA4', 'alice', 'Same Instant B',
   '[]'::jsonb, '2026-01-07 09:00:00', '2026-01-07 09:00:00'),
  ('01ARZ3NDEKTSV4RRFFQ69G5FB1', 'bob', 'Bob Solo Chat',
   '[{"role":"USER","message":"Hello","timestamp":"2026-01-08T09:00:00Z"}]'::jsonb,
   '2026-01-08 09:00:00', '2026-01-08 09:00:00');
-- carol intentionally has zero chats (findChats-on-empty-user case).
-- FA3/FA4 share created_at to exercise the CHAT_ID.desc() tiebreaker.
