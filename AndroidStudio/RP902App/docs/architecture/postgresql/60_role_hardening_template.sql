-- TraceLink PostgreSQL contract implementation: role and privilege hardening template.
--
-- Run after:
--   00_schema.sql
--   10_internal_tables.sql
--   20_helpers.sql
--   30_api_functions.sql
--
-- This file uses deterministic role names for local smoke testing and DBA review:
--   tracelink_api_owner
--   tracelink_android_app
--
-- Production DBAs may replace these names, but the privilege shape should stay the same:
-- Android gets api schema usage and api.fn_* execute only.
--
-- Ownership model:
--   tracelink_api_owner:
--     Owns api functions and internal storage. It should not be used by Android.
--   tracelink_android_app:
--     Login role used by Android. It should not own database objects.

begin;

do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'tracelink_api_owner') then
        execute 'create role tracelink_api_owner nologin noinherit';
    end if;

    if not exists (select 1 from pg_roles where rolname = 'tracelink_android_app') then
        execute 'create role tracelink_android_app login noinherit';
    end if;
end;
$$;

-- SECURITY DEFINER functions are safer when untrusted users cannot create objects in public.
revoke create on schema public from public;

-- Remove broad or accidental grants before applying the narrow Android grant shape below.
revoke all on schema api from public;
revoke all on schema api from tracelink_android_app;
revoke all on schema tracelink_internal from public;
revoke all on schema tracelink_internal from tracelink_android_app;

revoke all on all tables in schema tracelink_internal from public;
revoke all on all tables in schema tracelink_internal from tracelink_android_app;
revoke all on all sequences in schema tracelink_internal from public;
revoke all on all sequences in schema tracelink_internal from tracelink_android_app;
revoke execute on all functions in schema api from public;
revoke execute on all functions in schema api from tracelink_android_app;
revoke execute on all functions in schema tracelink_internal from public;
revoke execute on all functions in schema tracelink_internal from tracelink_android_app;

alter schema api owner to tracelink_api_owner;
alter schema tracelink_internal owner to tracelink_api_owner;

do $$
declare
    object_row record;
begin
    for object_row in
        select
            n.nspname as schema_name,
            c.relname as object_name,
            case c.relkind
                when 'S' then 'sequence'
                else 'table'
            end as object_type
        from pg_class c
        join pg_namespace n
            on n.oid = c.relnamespace
        where n.nspname in ('tracelink_internal')
            and c.relkind in ('r', 'p', 'S')
    loop
        execute format(
            'alter %s %I.%I owner to tracelink_api_owner',
            object_row.object_type,
            object_row.schema_name,
            object_row.object_name
        );
    end loop;
end;
$$;

do $$
declare
    function_row record;
begin
    for function_row in
        select
            n.nspname as schema_name,
            p.proname as function_name,
            pg_get_function_identity_arguments(p.oid) as arguments
        from pg_proc p
        join pg_namespace n
            on n.oid = p.pronamespace
        where n.nspname in ('api', 'tracelink_internal')
    loop
        execute format(
            'alter function %I.%I(%s) owner to tracelink_api_owner',
            function_row.schema_name,
            function_row.function_name,
            function_row.arguments
        );
    end loop;
end;
$$;

grant usage on schema api to tracelink_android_app;

grant execute on function api.fn_get_active_work_context(text) to tracelink_android_app;
grant execute on function api.fn_get_rule_bundle(text) to tracelink_android_app;
grant execute on function api.fn_get_equipment_snapshot(text) to tracelink_android_app;
grant execute on function api.fn_register_read_result_bundle(jsonb) to tracelink_android_app;

-- Set a password or external authentication outside source control.
-- Example for local-only smoke testing:
-- alter role tracelink_android_app with password '<local smoke password>';
--
-- DBA review notes:
-- - Do not grant tracelink_android_app direct privileges on tracelink_internal.
-- - Do not make tracelink_android_app the owner of SECURITY DEFINER functions.
-- - Keep api.fn_* search_path fixed in 30_api_functions.sql.
-- - If pgcrypto is installed in public, keep CREATE revoked from public.
-- - Prefer certificate or managed secret distribution for production credentials.

commit;
