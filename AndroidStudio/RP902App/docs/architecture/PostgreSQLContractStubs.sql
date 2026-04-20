-- TraceLink Android/PostgreSQL contract implementation runner.
-- Run this file with psql from docs/architecture.
--
-- The Android-facing contract is defined in PostgreSQLAccessContract.md.
-- The executable SQL is split so schema, tables, helpers, functions, seed data,
-- and manual checks can be reviewed independently.

\ir postgresql/00_schema.sql
\ir postgresql/10_internal_tables.sql
\ir postgresql/20_helpers.sql
\ir postgresql/30_api_functions.sql

-- Optional verification data and checks:
-- \ir postgresql/40_seed_test_data.sql
-- \ir postgresql/50_verification_queries.sql
