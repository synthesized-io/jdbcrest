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
