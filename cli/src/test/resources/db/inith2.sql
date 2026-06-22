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
