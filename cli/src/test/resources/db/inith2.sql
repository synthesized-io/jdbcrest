-- Schema and tables for the embedded H2 comparison context.
-- H2 runs in PostgreSQL compatibility mode with DATABASE_TO_LOWER=TRUE, so unquoted
-- identifiers fold to lower case and live in the lower-case "public" schema, matching
-- the quoted, lower-case identifiers jOOQ generates for the H2 dialect.

DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS empty_table;

CREATE TABLE users (
    id INTEGER PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE empty_table (
    id INTEGER PRIMARY KEY,
    note VARCHAR(255)
);

CREATE TABLE products (
    id INTEGER PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    price NUMERIC(10,2) NOT NULL DEFAULT 0
);

-- Seed data
INSERT INTO users(id, name) VALUES (1, 'Alice');
INSERT INTO users(id, name) VALUES (2, 'Bob');

INSERT INTO products(id, title, price) VALUES (100, 'Apples', 1.99);
INSERT INTO products(id, title, price) VALUES (101, 'Bananas', 0.99);
INSERT INTO products(id, title, price) VALUES (102, 'Carrots', 2.49);

-- One column per type whose JSON rendering is easy to get wrong, for FieldSerializationComparisonTest;
-- the same table as in init.sql. H2 has no jsonb, inet or xml type, cannot hold an interval of 25 hours
-- or one whose parts differ in sign, and returns the elements of a TIME array without their fraction,
-- so those columns are left out and the corresponding tests are skipped by assumption.
DROP TABLE IF EXISTS type_samples;

CREATE TABLE type_samples (
    id            INTEGER PRIMARY KEY,
    c_date        DATE,
    c_json        JSON,
    c_timestamp   TIMESTAMP(6),
    c_timestamptz TIMESTAMP(6) WITH TIME ZONE,
    c_time        TIME(1),
    c_timetz      TIME(1) WITH TIME ZONE,
    c_interval    INTERVAL DAY TO SECOND,
    c_bytea       VARBINARY(16),
    c_numeric     NUMERIC(10,2),
    c_bigint      BIGINT,
    c_int_array   INTEGER ARRAY,
    c_text_array  VARCHAR ARRAY,
    c_uuid        UUID,
    c_bool        BOOLEAN,
    c_float8      DOUBLE PRECISION
);

INSERT INTO type_samples VALUES (
    1,
    DATE '2024-01-07',
    JSON '{"tier":"gold"}',
    TIMESTAMP '2024-01-07 04:30:57.319412',
    TIMESTAMP WITH TIME ZONE '2024-01-07 04:30:57.319412+02',
    TIME '04:30:57.5',
    TIME WITH TIME ZONE '04:30:57.5+02',
    INTERVAL '1 02:03:04' DAY TO SECOND,
    X'DEADBEEF',
    1.50,
    9007199254740993,
    ARRAY[1, 2, 3],
    ARRAY['a', 'b'],
    CAST('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11' AS UUID),
    TRUE,
    1.5
);

INSERT INTO type_samples (id) VALUES (2);
