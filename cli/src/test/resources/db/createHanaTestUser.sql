-- Create test user for SAP HANA (runs as SYSTEM)
-- This script creates just the user, without tables - used for "empty" container
-- When a user is created, SAP HANA automatically creates a schema with the same name
CREATE USER TESTUSER PASSWORD "TestUser123" NO FORCE_FIRST_PASSWORD_CHANGE;
GRANT CREATE SCHEMA TO TESTUSER;
