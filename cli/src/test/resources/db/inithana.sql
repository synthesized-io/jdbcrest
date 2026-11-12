SET SCHEMA TESTUSER;

CREATE TABLE "users" (
                              "id" INTEGER PRIMARY KEY,
                              "name" VARCHAR(200) NOT NULL
);

CREATE TABLE "empty_table" (
                                    "id" INTEGER PRIMARY KEY,
                                    "note" TEXT
);

CREATE TABLE "products" (
                                 "id" INTEGER PRIMARY KEY,
                                 "title" TEXT NOT NULL,
                                 "price" NUMERIC(10,2) NOT NULL DEFAULT 0
);

-- Seed data
INSERT INTO "users"("id", "name") VALUES (1, 'Alice');
INSERT INTO "users"("id", "name") VALUES (2, 'Bob');

INSERT INTO "products"("id", "title", "price") VALUES (100, 'Apples', 1.99);
INSERT INTO "products"("id", "title", "price") VALUES (101, 'Bananas', 0.99);
INSERT INTO "products"("id", "title", "price") VALUES (102, 'Carrots', 2.49);

-- One column per type whose JSON rendering is easy to get wrong, for FieldSerializationComparisonTest;
-- the same table as in init.sql. SAP HANA has no json, inet, time zone aware, interval, xml or uuid
-- types, its TIME has no fractional seconds, and array columns are not readable through jOOQ, so
-- those columns are left out and the corresponding tests are skipped by assumption.
CREATE TABLE "type_samples" (
    "id"          INTEGER PRIMARY KEY,
    "c_date"      DATE,
    "c_timestamp" TIMESTAMP,
    "c_bytea"     VARBINARY(16),
    "c_numeric"   DECIMAL(10,2),
    "c_bigint"    BIGINT,
    "c_bool"      BOOLEAN,
    "c_float8"    DOUBLE
);

INSERT INTO "type_samples" VALUES (
    1,
    '2024-01-07',
    '2024-01-07 04:30:57.319412',
    X'DEADBEEF',
    1.50,
    9007199254740993,
    TRUE,
    1.5
);

INSERT INTO "type_samples" ("id") VALUES (2);
