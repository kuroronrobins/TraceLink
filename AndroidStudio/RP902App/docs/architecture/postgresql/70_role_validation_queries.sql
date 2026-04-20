-- TraceLink PostgreSQL contract implementation: least-privilege validation queries.
--
-- Run after:
--   PostgreSQLContractStubs.sql
--   postgresql/40_seed_test_data.sql
--   postgresql/60_role_hardening_template.sql
--
-- Execute as an admin role that can SET ROLE to tracelink_android_app.

delete from tracelink_internal.read_result_session
where device_id = 'android-local-device'
    and session_id = 'role-validation-session-1';

select
    'android_has_api_schema_usage' as check_name,
    has_schema_privilege('tracelink_android_app', 'api', 'USAGE') as passed;

select
    'android_lacks_internal_schema_usage' as check_name,
    not has_schema_privilege('tracelink_android_app', 'tracelink_internal', 'USAGE') as passed;

select
    'android_lacks_internal_table_select' as check_name,
    not has_table_privilege('tracelink_android_app', 'tracelink_internal.device_registry', 'SELECT') as passed;

select
    'android_can_execute_get_active_work_context' as check_name,
    has_function_privilege(
        'tracelink_android_app',
        'api.fn_get_active_work_context(text)',
        'EXECUTE'
    ) as passed;

select
    'android_can_execute_get_rule_bundle' as check_name,
    has_function_privilege(
        'tracelink_android_app',
        'api.fn_get_rule_bundle(text)',
        'EXECUTE'
    ) as passed;

select
    'android_can_execute_get_equipment_snapshot' as check_name,
    has_function_privilege(
        'tracelink_android_app',
        'api.fn_get_equipment_snapshot(text)',
        'EXECUTE'
    ) as passed;

select
    'android_can_execute_register_read_result_bundle' as check_name,
    has_function_privilege(
        'tracelink_android_app',
        'api.fn_register_read_result_bundle(jsonb)',
        'EXECUTE'
    ) as passed;

select
    'public_create_revoked_from_public' as check_name,
    not exists (
        select 1
        from pg_namespace n
        cross join lateral aclexplode(coalesce(n.nspacl, acldefault('n', n.nspowner))) acl
        where n.nspname = 'public'
            and acl.grantee = 0
            and acl.privilege_type = 'CREATE'
    ) as passed;

select
    p.proname as function_name,
    p.prosecdef as security_definer,
    p.proconfig as function_settings
from pg_proc p
join pg_namespace n
    on n.oid = p.pronamespace
where n.nspname = 'api'
    and p.proname in (
        'fn_get_active_work_context',
        'fn_get_rule_bundle',
        'fn_get_equipment_snapshot',
        'fn_register_read_result_bundle'
    )
order by p.proname;

set role tracelink_android_app;

select *
from api.fn_get_active_work_context('android-local-device');

select *
from api.fn_get_rule_bundle('work-1');

select *
from api.fn_get_equipment_snapshot('work-1');

select *
from api.fn_register_read_result_bundle(
    '{
        "schemaVersion": 1,
        "sessionId": "role-validation-session-1",
        "registeredAtEpochMillis": 1712300000600,
        "deviceId": "android-local-device",
        "readerType": "RP902",
        "workId": "work-1",
        "reportId": "report-1",
        "operatorId": "operator-1",
        "ruleVersion": "rule-v1",
        "equipmentSnapshotVersion": "equipment-v1",
        "tags": [
            {
                "epc": "E2806894000040035A1F90A1",
                "firstSeenAtEpochMillis": 1712300000100,
                "lastSeenAtEpochMillis": 1712300000300,
                "readCount": 2,
                "judgementStatus": "Accepted",
                "judgementReasonCode": null
            }
        ]
    }'::jsonb
);

reset role;

-- Manual negative check:
-- The next query must fail with permission denied when run under tracelink_android_app.
-- Keep it commented so the whole validation file can complete.
--
-- set role tracelink_android_app;
-- select count(*) from tracelink_internal.device_registry;
-- reset role;
