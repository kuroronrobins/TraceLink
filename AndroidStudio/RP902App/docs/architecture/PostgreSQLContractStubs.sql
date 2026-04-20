-- TraceLink Android/PostgreSQL contract stubs.
-- These stubs document the public api schema expected by the Android app.
-- They are not production implementations.

create schema if not exists api;

create or replace function api.fn_get_active_work_context(
    p_device_id text
)
returns table (
    work_id text,
    report_id text,
    operator_id text,
    started_at_epoch_millis bigint
)
language plpgsql
as $$
begin
    -- TODO: Resolve exactly one active work context for p_device_id.
    -- Return 0 rows only if the Android contract should fail fast.
    raise exception 'api.fn_get_active_work_context is not implemented'
        using errcode = '0A000';
end;
$$;

create or replace function api.fn_get_rule_bundle(
    p_work_id text
)
returns table (
    rule_version text,
    report_id text,
    effective_at_epoch_millis bigint,
    target_equipment_types text[]
)
language plpgsql
as $$
begin
    -- TODO: Return exactly one row for p_work_id.
    raise exception 'api.fn_get_rule_bundle is not implemented'
        using errcode = '0A000';
end;
$$;

create or replace function api.fn_get_equipment_snapshot(
    p_work_id text
)
returns table (
    snapshot_version text,
    captured_at_epoch_millis bigint,
    equipment_id text,
    epc text,
    equipment_type text,
    display_name text
)
language plpgsql
as $$
begin
    -- TODO: Return one row per equipment record. Empty snapshots are not allowed.
    raise exception 'api.fn_get_equipment_snapshot is not implemented'
        using errcode = '0A000';
end;
$$;

create or replace function api.fn_register_read_result_bundle(
    p_bundle jsonb
)
returns table (
    success boolean,
    duplicate boolean,
    accepted_session_id text,
    result_id uuid,
    failure_kind text,
    error_code text,
    message text
)
language plpgsql
as $$
begin
    -- TODO: Validate JSON schemaVersion=1 and enforce unique(device_id, session_id).
    -- Return validation failures as a single row instead of raising an exception.
    return query
    select
        false as success,
        false as duplicate,
        null::text as accepted_session_id,
        null::uuid as result_id,
        'contract'::text as failure_kind,
        'not_implemented'::text as error_code,
        'api.fn_register_read_result_bundle is not implemented'::text as message;
end;
$$;

-- Sample registration payload accepted by api.fn_register_read_result_bundle:
--
-- {
--   "schemaVersion": 1,
--   "sessionId": "session-1",
--   "registeredAtEpochMillis": 1712300000000,
--   "deviceId": "android-local-device",
--   "readerType": "RP902",
--   "workId": "work-1",
--   "reportId": "report-1",
--   "operatorId": "operator-1",
--   "ruleVersion": "rule-v1",
--   "equipmentSnapshotVersion": "equipment-v1",
--   "tags": [
--     {
--       "epc": "E2806894000040035A1F90A1",
--       "firstSeenAtEpochMillis": 1712300000100,
--       "lastSeenAtEpochMillis": 1712300000300,
--       "readCount": 2,
--       "judgementStatus": "Accepted",
--       "judgementReasonCode": null
--     }
--   ]
-- }
--
-- Sample successful result row:
--
-- select
--   true as success,
--   false as duplicate,
--   'session-1'::text as accepted_session_id,
--   '00000000-0000-0000-0000-000000000001'::uuid as result_id,
--   null::text as failure_kind,
--   null::text as error_code,
--   'registered'::text as message;
