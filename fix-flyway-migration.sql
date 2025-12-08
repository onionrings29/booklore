-- Fix Flyway migration version conflict
-- This updates the schema history to reflect that V64 was renamed to V66

-- Step 1: Check current state
SELECT version, description, checksum, installed_rank
FROM flyway_schema_history
WHERE version IN ('64', '65', '66')
ORDER BY installed_rank;

-- Step 2: Update V64 entry to V66 (the ephemera migration was renamed)
UPDATE flyway_schema_history
SET version = '66',
    description = 'Create user ephemera settings table',
    script = 'V66__Create_user_ephemera_settings_table.sql'
WHERE version = '64'
  AND checksum = 1955141590;

-- Step 3: Verify the change
SELECT version, description, checksum, installed_rank
FROM flyway_schema_history
WHERE version IN ('64', '65', '66')
ORDER BY installed_rank;

-- Now Flyway will apply the new V64 (Unique_book_subpath) and V65 (Kobo reading status) on next startup
