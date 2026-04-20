-- TraceLink PostgreSQL contract implementation: internal helper functions.

create or replace function tracelink_internal.fn_epoch_millis()
returns bigint
language sql
stable
as $$
    select (extract(epoch from clock_timestamp()) * 1000)::bigint;
$$;

create or replace function tracelink_internal.fn_payload_hash(
    p_payload jsonb
)
returns bytea
language sql
immutable
as $$
    select digest(p_payload::text, 'sha256');
$$;

create or replace function tracelink_internal.fn_registration_result(
    p_success boolean,
    p_duplicate boolean,
    p_accepted_session_id text,
    p_result_id uuid,
    p_failure_kind text,
    p_error_code text,
    p_message text
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
language sql
stable
as $$
    select
        p_success,
        p_duplicate,
        p_accepted_session_id,
        p_result_id,
        p_failure_kind,
        p_error_code,
        p_message;
$$;

create or replace function tracelink_internal.fn_registration_failure(
    p_failure_kind text,
    p_error_code text,
    p_message text
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
language sql
stable
as $$
    select *
    from tracelink_internal.fn_registration_result(
        false,
        false,
        null::text,
        null::uuid,
        p_failure_kind,
        p_error_code,
        p_message
    );
$$;

create or replace function tracelink_internal.fn_registration_success(
    p_duplicate boolean,
    p_accepted_session_id text,
    p_result_id uuid,
    p_message text
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
language sql
stable
as $$
    select *
    from tracelink_internal.fn_registration_result(
        true,
        p_duplicate,
        p_accepted_session_id,
        p_result_id,
        null::text,
        null::text,
        p_message
    );
$$;

create or replace function tracelink_internal.fn_audit_registration_failure(
    p_bundle jsonb,
    p_failure_kind text,
    p_error_code text,
    p_message text,
    p_payload_hash bytea default null,
    p_result_id uuid default null
)
returns void
language plpgsql
volatile
as $$
declare
    v_device_id text;
    v_session_id text;
    v_event_type text;
begin
    if p_bundle is not null and jsonb_typeof(p_bundle) = 'object' then
        if jsonb_typeof(p_bundle -> 'deviceId') = 'string' then
            v_device_id := p_bundle ->> 'deviceId';
        end if;
        if jsonb_typeof(p_bundle -> 'sessionId') = 'string' then
            v_session_id := p_bundle ->> 'sessionId';
        end if;
    end if;

    v_event_type := case p_failure_kind
        when 'contract' then 'contract_failure'
        when 'configuration' then 'configuration_failure'
        when 'retryable' then 'retryable_failure'
        else 'unknown_failure'
    end;

    insert into tracelink_internal.read_result_audit (
        result_id,
        device_id,
        session_id,
        event_type,
        failure_kind,
        error_code,
        message,
        payload_hash,
        payload
    )
    values (
        p_result_id,
        v_device_id,
        v_session_id,
        v_event_type,
        p_failure_kind,
        p_error_code,
        p_message,
        coalesce(p_payload_hash, tracelink_internal.fn_payload_hash(p_bundle)),
        p_bundle
    );
end;
$$;

create or replace function tracelink_internal.fn_jsonb_has_only_keys(
    p_object jsonb,
    p_allowed text[]
)
returns boolean
language sql
immutable
as $$
    select not exists (
        select 1
        from jsonb_object_keys(p_object) as keys(key)
        where not (keys.key = any (p_allowed))
    );
$$;

create or replace function tracelink_internal.fn_jsonb_first_unknown_key(
    p_object jsonb,
    p_allowed text[]
)
returns text
language sql
immutable
as $$
    select keys.key
    from jsonb_object_keys(p_object) as keys(key)
    where not (keys.key = any (p_allowed))
    order by keys.key
    limit 1;
$$;

create or replace function tracelink_internal.fn_jsonb_first_missing_key(
    p_object jsonb,
    p_required text[]
)
returns text
language sql
immutable
as $$
    select required.key
    from unnest(p_required) as required(key)
    where not (p_object ? required.key)
    order by required.key
    limit 1;
$$;

create or replace function tracelink_internal.fn_jsonb_is_integer(
    p_value jsonb
)
returns boolean
language sql
immutable
as $$
    select jsonb_typeof(p_value) = 'number'
        and (p_value #>> '{}') ~ '^-?[0-9]+$';
$$;

create or replace function tracelink_internal.fn_validate_read_result_bundle(
    p_bundle jsonb
)
returns table (
    failure_kind text,
    error_code text,
    message text
)
language plpgsql
stable
as $$
declare
    v_top_allowed constant text[] := array[
        'schemaVersion',
        'sessionId',
        'registeredAtEpochMillis',
        'deviceId',
        'readerType',
        'workId',
        'reportId',
        'operatorId',
        'ruleVersion',
        'equipmentSnapshotVersion',
        'tags'
    ];
    v_top_required constant text[] := array[
        'schemaVersion',
        'sessionId',
        'registeredAtEpochMillis',
        'deviceId',
        'readerType',
        'workId',
        'reportId',
        'operatorId',
        'ruleVersion',
        'equipmentSnapshotVersion',
        'tags'
    ];
    v_tag_allowed constant text[] := array[
        'epc',
        'firstSeenAtEpochMillis',
        'lastSeenAtEpochMillis',
        'readCount',
        'judgementStatus',
        'judgementReasonCode'
    ];
    v_tag_required constant text[] := array[
        'epc',
        'firstSeenAtEpochMillis',
        'lastSeenAtEpochMillis',
        'readCount',
        'judgementStatus',
        'judgementReasonCode'
    ];
    v_unknown_key text;
    v_missing_key text;
    v_tag jsonb;
    v_tag_index integer := 0;
    v_first_seen bigint;
    v_last_seen bigint;
    v_read_count bigint;
    v_status text;
    v_reason text;
    v_epc text;
    v_seen_epcs text[] := array[]::text[];
begin
    if p_bundle is null or jsonb_typeof(p_bundle) <> 'object' then
        return query select 'contract', 'invalid_type', 'Registration bundle must be a JSON object.';
        return;
    end if;

    v_unknown_key := tracelink_internal.fn_jsonb_first_unknown_key(p_bundle, v_top_allowed);
    if v_unknown_key is not null then
        return query select 'contract', 'unknown_field', 'Unknown top-level field: ' || v_unknown_key;
        return;
    end if;

    v_missing_key := tracelink_internal.fn_jsonb_first_missing_key(p_bundle, v_top_required);
    if v_missing_key is not null then
        return query select 'contract', 'missing_required_field', 'Missing top-level field: ' || v_missing_key;
        return;
    end if;

    if not tracelink_internal.fn_jsonb_is_integer(p_bundle -> 'schemaVersion')
        or (p_bundle ->> 'schemaVersion')::bigint <> 1 then
        return query select 'contract', 'invalid_schema_version', 'schemaVersion must be 1.';
        return;
    end if;

    foreach v_missing_key in array array[
        'sessionId',
        'deviceId',
        'readerType',
        'workId',
        'reportId',
        'ruleVersion',
        'equipmentSnapshotVersion'
    ]
    loop
        if jsonb_typeof(p_bundle -> v_missing_key) <> 'string'
            or btrim(p_bundle ->> v_missing_key) = '' then
            return query select 'contract', 'invalid_type', v_missing_key || ' must be a non-empty string.';
            return;
        end if;
    end loop;

    if jsonb_typeof(p_bundle -> 'operatorId') not in ('string', 'null') then
        return query select 'contract', 'invalid_type', 'operatorId must be a string or null.';
        return;
    end if;

    if jsonb_typeof(p_bundle -> 'operatorId') = 'string'
        and btrim(p_bundle ->> 'operatorId') = '' then
        return query select 'contract', 'invalid_type', 'operatorId must not be blank when present.';
        return;
    end if;

    if not tracelink_internal.fn_jsonb_is_integer(p_bundle -> 'registeredAtEpochMillis')
        or (p_bundle ->> 'registeredAtEpochMillis')::bigint < 0 then
        return query select 'contract', 'invalid_type', 'registeredAtEpochMillis must be a non-negative integer.';
        return;
    end if;

    if jsonb_typeof(p_bundle -> 'tags') <> 'array' then
        return query select 'contract', 'invalid_type', 'tags must be an array.';
        return;
    end if;

    if jsonb_array_length(p_bundle -> 'tags') = 0 then
        return query select 'contract', 'empty_tags', 'tags must contain at least one tag.';
        return;
    end if;

    for v_tag in
        select value
        from jsonb_array_elements(p_bundle -> 'tags')
    loop
        v_tag_index := v_tag_index + 1;

        if jsonb_typeof(v_tag) <> 'object' then
            return query select 'contract', 'invalid_type', 'tags[' || v_tag_index || '] must be an object.';
            return;
        end if;

        v_unknown_key := tracelink_internal.fn_jsonb_first_unknown_key(v_tag, v_tag_allowed);
        if v_unknown_key is not null then
            return query select 'contract', 'unknown_field', 'Unknown tag field: ' || v_unknown_key;
            return;
        end if;

        v_missing_key := tracelink_internal.fn_jsonb_first_missing_key(v_tag, v_tag_required);
        if v_missing_key is not null then
            return query select 'contract', 'missing_required_field', 'Missing tag field: ' || v_missing_key;
            return;
        end if;

        foreach v_missing_key in array array['epc', 'judgementStatus']
        loop
            if jsonb_typeof(v_tag -> v_missing_key) <> 'string'
                or btrim(v_tag ->> v_missing_key) = '' then
                return query select 'contract', 'invalid_type', 'tags[' || v_tag_index || '].' || v_missing_key || ' must be a non-empty string.';
                return;
            end if;
        end loop;

        v_epc := v_tag ->> 'epc';
        if v_epc <> upper(btrim(v_epc)) then
            return query select 'contract', 'invalid_type', 'tags[' || v_tag_index || '].epc must be normalized.';
            return;
        end if;

        if v_epc = any (v_seen_epcs) then
            return query select 'contract', 'duplicate_epc', 'Duplicate EPC in tags: ' || v_epc;
            return;
        end if;
        v_seen_epcs := array_append(v_seen_epcs, v_epc);

        if not tracelink_internal.fn_jsonb_is_integer(v_tag -> 'firstSeenAtEpochMillis')
            or not tracelink_internal.fn_jsonb_is_integer(v_tag -> 'lastSeenAtEpochMillis')
            or not tracelink_internal.fn_jsonb_is_integer(v_tag -> 'readCount') then
            return query select 'contract', 'invalid_type', 'tag time fields and readCount must be integers.';
            return;
        end if;

        v_first_seen := (v_tag ->> 'firstSeenAtEpochMillis')::bigint;
        v_last_seen := (v_tag ->> 'lastSeenAtEpochMillis')::bigint;
        v_read_count := (v_tag ->> 'readCount')::bigint;

        if v_first_seen < 0 or v_last_seen < 0 then
            return query select 'contract', 'invalid_type', 'tag epoch millis must be non-negative.';
            return;
        end if;

        if v_last_seen < v_first_seen then
            return query select 'contract', 'invalid_time_range', 'lastSeenAtEpochMillis must be greater than or equal to firstSeenAtEpochMillis.';
            return;
        end if;

        if v_read_count < 1 then
            return query select 'contract', 'invalid_type', 'readCount must be greater than or equal to 1.';
            return;
        end if;

        v_status := v_tag ->> 'judgementStatus';
        if v_status not in ('Accepted', 'Excluded', 'Ng') then
            return query select 'contract', 'invalid_judgement_status', 'Unknown judgementStatus: ' || v_status;
            return;
        end if;

        if jsonb_typeof(v_tag -> 'judgementReasonCode') not in ('string', 'null') then
            return query select 'contract', 'invalid_type', 'judgementReasonCode must be a string or null.';
            return;
        end if;

        v_reason := v_tag ->> 'judgementReasonCode';
        if v_status = 'Accepted' and v_reason is not null then
            return query select 'contract', 'invalid_judgement_status', 'Accepted tags must not have judgementReasonCode.';
            return;
        end if;

        if v_status <> 'Accepted' and (v_reason is null or btrim(v_reason) = '') then
            return query select 'contract', 'invalid_judgement_status', 'Non-Accepted tags must have judgementReasonCode.';
            return;
        end if;
    end loop;

    return;
end;
$$;
