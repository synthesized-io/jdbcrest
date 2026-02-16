-- Ensure anon role for PostgREST exists
DO $$
BEGIN
   IF NOT EXISTS (
      SELECT FROM pg_catalog.pg_roles WHERE rolname = 'web_anon') THEN
      CREATE ROLE web_anon NOLOGIN;
   END IF;
END
$$;

-- Schema and tables
DROP TABLE IF EXISTS public.users;
DROP TABLE IF EXISTS public.empty_table;
CREATE TABLE public.users (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL
);
CREATE TABLE public.empty_table (
    id INTEGER PRIMARY KEY,
    note TEXT
);

-- Seed data
INSERT INTO public.users(id, name) VALUES (1, 'Alice');
INSERT INTO public.users(id, name) VALUES (2, 'Bob');

-- Grants for PostgREST anon role
GRANT USAGE ON SCHEMA public TO web_anon;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO web_anon;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO web_anon;
