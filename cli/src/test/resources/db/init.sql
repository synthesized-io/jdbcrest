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
DROP TABLE IF EXISTS public.products;
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

CREATE TABLE public.products (
    id INTEGER PRIMARY KEY,
    title TEXT NOT NULL,
    price NUMERIC(10,2) NOT NULL DEFAULT 0
);

-- Seed data
INSERT INTO public.users(id, name) VALUES (1, 'Alice');
INSERT INTO public.users(id, name) VALUES (2, 'Bob');

INSERT INTO public.products(id, title, price) VALUES (100, 'Apples', 1.99);
INSERT INTO public.products(id, title, price) VALUES (101, 'Bananas', 0.99);
INSERT INTO public.products(id, title, price) VALUES (102, 'Carrots', 2.49);

-- Grants for PostgREST anon role
GRANT USAGE ON SCHEMA public TO web_anon;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO web_anon;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO web_anon;

-- One column per type whose JSON rendering is easy to get wrong (dates and times, intervals, binary,
-- json, arrays, xml, exact numerics), for FieldSerializationComparisonTest. Row 1 holds values that
-- exercise precision, offset and scale, row 2 holds NULLs.
DROP TABLE IF EXISTS public.type_samples;

CREATE TABLE public.type_samples (
    id            INTEGER PRIMARY KEY,
    c_date        DATE,
    c_json        JSON,
    c_jsonb       JSONB,
    c_inet        INET,
    c_timestamp   TIMESTAMP,
    c_timestamptz TIMESTAMPTZ,
    c_time        TIME,
    c_timetz      TIMETZ,
    c_interval    INTERVAL,
    c_bytea       BYTEA,
    c_numeric     NUMERIC(10,2),
    c_bigint      BIGINT,
    c_int_array   INTEGER[],
    c_text_array  TEXT[],
    c_xml         XML,
    c_uuid        UUID,
    c_bool        BOOLEAN,
    c_float8      DOUBLE PRECISION
);

INSERT INTO public.type_samples VALUES (
    1,
    DATE '2024-01-07',
    '{"tier":"gold"}',
    '{"tier":"gold"}',
    '192.168.0.1/24',
    TIMESTAMP '2024-01-07 04:30:57.319412',
    TIMESTAMPTZ '2024-01-07 04:30:57.319412+02',
    TIME '04:30:57.5',
    TIMETZ '04:30:57.5+02',
    INTERVAL '1 day 02:03:04',
    '\xdeadbeef',
    1.50,
    9007199254740993,
    '{1,2,3}',
    '{"a","b"}',
    '<a>1</a>',
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    TRUE,
    1.5
);

INSERT INTO public.type_samples (id) VALUES (2);

GRANT SELECT ON public.type_samples TO web_anon;
