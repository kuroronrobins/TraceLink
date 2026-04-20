-- TraceLink PostgreSQL contract implementation: manual verification queries.
-- Run after 00_schema.sql, 10_internal_tables.sql, 20_helpers.sql, 30_api_functions.sql, and 40_seed_test_data.sql.

delete from tracelink_internal.read_result_session
where device_id = 'android-local-device'
    and session_id in (
        'verification-session-1',
        'verification-session-unknown-field',
        'verification-session-empty-tags'
    );

-- Work context: exactly one row for the seeded device.
select *
from api.fn_get_active_work_context('android-local-device');

-- Rule bundle: exactly one row for the seeded work.
select *
from api.fn_get_rule_bundle('work-1');

-- Equipment snapshot: one or more rows. Android must not rely on row order.
select *
from api.fn_get_equipment_snapshot('work-1');

-- Successful first registration.
select *
from api.fn_register_read_result_bundle(
    '{
        "schemaVersion": 1,
        "sessionId": "verification-session-1",
        "registeredAtEpochMillis": 1712300000500,
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
            },
            {
                "epc": "E2806894000040035A1F90A2",
                "firstSeenAtEpochMillis": 1712300000200,
                "lastSeenAtEpochMillis": 1712300000400,
                "readCount": 2,
                "judgementStatus": "Accepted",
                "judgementReasonCode": null
            }
        ]
    }'::jsonb
);

-- Idempotent retry: same deviceId/sessionId and same payload must be duplicate success.
select *
from api.fn_register_read_result_bundle(
    '{
        "schemaVersion": 1,
        "sessionId": "verification-session-1",
        "registeredAtEpochMillis": 1712300000500,
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
            },
            {
                "epc": "E2806894000040035A1F90A2",
                "firstSeenAtEpochMillis": 1712300000200,
                "lastSeenAtEpochMillis": 1712300000400,
                "readCount": 2,
                "judgementStatus": "Accepted",
                "judgementReasonCode": null
            }
        ]
    }'::jsonb
);

-- Idempotency mismatch: same deviceId/sessionId with different payload is a contract failure.
select *
from api.fn_register_read_result_bundle(
    '{
        "schemaVersion": 1,
        "sessionId": "verification-session-1",
        "registeredAtEpochMillis": 1712300000500,
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
                "readCount": 3,
                "judgementStatus": "Accepted",
                "judgementReasonCode": null
            }
        ]
    }'::jsonb
);

-- Unknown top-level fields are rejected as contract failures.
select *
from api.fn_register_read_result_bundle(
    '{
        "schemaVersion": 1,
        "sessionId": "verification-session-unknown-field",
        "registeredAtEpochMillis": 1712300000500,
        "deviceId": "android-local-device",
        "readerType": "RP902",
        "workId": "work-1",
        "reportId": "report-1",
        "operatorId": "operator-1",
        "ruleVersion": "rule-v1",
        "equipmentSnapshotVersion": "equipment-v1",
        "unexpectedField": "must-not-be-accepted",
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

-- Empty tags are rejected as contract failures.
select *
from api.fn_register_read_result_bundle(
    '{
        "schemaVersion": 1,
        "sessionId": "verification-session-empty-tags",
        "registeredAtEpochMillis": 1712300000500,
        "deviceId": "android-local-device",
        "readerType": "RP902",
        "workId": "work-1",
        "reportId": "report-1",
        "operatorId": "operator-1",
        "ruleVersion": "rule-v1",
        "equipmentSnapshotVersion": "equipment-v1",
        "tags": []
    }'::jsonb
);

-- Getter contract failures are SQL exceptions because their public return shape has no failure row.
do $$
begin
    perform *
    from api.fn_get_active_work_context('unassigned-device');

    raise exception 'Expected device_not_assigned exception was not raised.';
exception
    when no_data_found then
        raise notice 'Expected device_not_assigned exception was raised.';
end;
$$;

do $$
begin
    perform *
    from api.fn_get_equipment_snapshot('missing-work');

    raise exception 'Expected empty_equipment_snapshot exception was not raised.';
exception
    when no_data_found then
        raise notice 'Expected empty_equipment_snapshot exception was raised.';
end;
$$;
