-- TraceLink PostgreSQL contract implementation: public api functions.
-- Android must use only these functions.

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
stable
security definer
set search_path = pg_catalog, public, api, tracelink_internal
as $$
declare
    v_count integer;
begin
    if p_device_id is null or btrim(p_device_id) = '' then
        raise exception 'device_not_assigned: p_device_id must be non-empty'
            using errcode = 'P0002';
    end if;

    select count(*)
    into v_count
    from tracelink_internal.device_work_assignment dwa
    join tracelink_internal.device_registry dr
        on dr.device_id = dwa.device_id
    join tracelink_internal.work_context wc
        on wc.work_id = dwa.work_id
    where dwa.device_id = p_device_id
        and dwa.is_active
        and dr.is_enabled
        and wc.status = 'active';

    if v_count = 0 then
        raise exception 'device_not_assigned: no active work context for device_id=%', p_device_id
            using errcode = 'P0002';
    end if;

    if v_count > 1 then
        raise exception 'multiple_active_work_contexts: device_id=% has % active work contexts', p_device_id, v_count
            using errcode = 'P0003';
    end if;

    return query
    select
        wc.work_id,
        wc.report_id,
        wc.operator_id,
        wc.started_at_epoch_millis
    from tracelink_internal.device_work_assignment dwa
    join tracelink_internal.device_registry dr
        on dr.device_id = dwa.device_id
    join tracelink_internal.work_context wc
        on wc.work_id = dwa.work_id
    where dwa.device_id = p_device_id
        and dwa.is_active
        and dr.is_enabled
        and wc.status = 'active';
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
stable
security definer
set search_path = pg_catalog, public, api, tracelink_internal
as $$
declare
    v_count integer;
begin
    if p_work_id is null or btrim(p_work_id) = '' then
        raise exception 'rule_bundle_not_found: p_work_id must be non-empty'
            using errcode = 'P0002';
    end if;

    select count(*)
    into v_count
    from tracelink_internal.rule_bundle rb
    join tracelink_internal.work_context wc
        on wc.work_id = rb.work_id
    where rb.work_id = p_work_id
        and rb.is_active
        and wc.status = 'active';

    if v_count = 0 then
        raise exception 'rule_bundle_not_found: no active rule bundle for work_id=%', p_work_id
            using errcode = 'P0002';
    end if;

    if v_count > 1 then
        raise exception 'multiple_rule_bundles: work_id=% has % active rule bundles', p_work_id, v_count
            using errcode = 'P0003';
    end if;

    return query
    select
        rb.rule_version,
        rb.report_id,
        rb.effective_at_epoch_millis,
        rb.target_equipment_types
    from tracelink_internal.rule_bundle rb
    join tracelink_internal.work_context wc
        on wc.work_id = rb.work_id
    where rb.work_id = p_work_id
        and rb.is_active
        and wc.status = 'active';
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
stable
security definer
set search_path = pg_catalog, public, api, tracelink_internal
as $$
declare
    v_snapshot_count integer;
    v_snapshot_id uuid;
    v_record_count integer;
begin
    if p_work_id is null or btrim(p_work_id) = '' then
        raise exception 'empty_equipment_snapshot: p_work_id must be non-empty'
            using errcode = 'P0002';
    end if;

    select count(*)
    into v_snapshot_count
    from tracelink_internal.equipment_snapshot es
    join tracelink_internal.work_context wc
        on wc.work_id = es.work_id
    where es.work_id = p_work_id
        and es.is_active
        and wc.status = 'active';

    if v_snapshot_count = 0 then
        raise exception 'empty_equipment_snapshot: no active equipment snapshot for work_id=%', p_work_id
            using errcode = 'P0002';
    end if;

    if v_snapshot_count > 1 then
        raise exception 'multiple_equipment_snapshots: work_id=% has % active equipment snapshots', p_work_id, v_snapshot_count
            using errcode = 'P0003';
    end if;

    select es.snapshot_id
    into v_snapshot_id
    from tracelink_internal.equipment_snapshot es
    where es.work_id = p_work_id
        and es.is_active;

    select count(*)
    into v_record_count
    from tracelink_internal.equipment_snapshot_record esr
    where esr.snapshot_id = v_snapshot_id;

    if v_record_count = 0 then
        raise exception 'empty_equipment_snapshot: active equipment snapshot has no records for work_id=%', p_work_id
            using errcode = 'P0002';
    end if;

    return query
    select
        es.snapshot_version,
        es.captured_at_epoch_millis,
        esr.equipment_id,
        esr.epc,
        esr.equipment_type,
        esr.display_name
    from tracelink_internal.equipment_snapshot es
    join tracelink_internal.equipment_snapshot_record esr
        on esr.snapshot_id = es.snapshot_id
    where es.snapshot_id = v_snapshot_id;
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
volatile
security definer
set search_path = pg_catalog, public, api, tracelink_internal
as $$
declare
    v_validation record;
    v_payload_hash bytea;
    v_existing tracelink_internal.read_result_session%rowtype;
    v_result_id uuid;
    v_device_id text;
    v_session_id text;
    v_registered_at_epoch_millis bigint;
    v_reader_type text;
    v_work_id text;
    v_report_id text;
    v_operator_id text;
    v_rule_version text;
    v_equipment_snapshot_version text;
    v_work_report_id text;
    v_snapshot_id uuid;
begin
    v_payload_hash := tracelink_internal.fn_payload_hash(p_bundle);

    select *
    into v_validation
    from tracelink_internal.fn_validate_read_result_bundle(p_bundle)
    limit 1;

    if found then
        perform tracelink_internal.fn_audit_registration_failure(
            p_bundle,
            v_validation.failure_kind,
            v_validation.error_code,
            v_validation.message,
            v_payload_hash
        );

        return query
        select *
        from tracelink_internal.fn_registration_failure(
            v_validation.failure_kind,
            v_validation.error_code,
            v_validation.message
        );
        return;
    end if;

    v_device_id := p_bundle ->> 'deviceId';
    v_session_id := p_bundle ->> 'sessionId';
    v_registered_at_epoch_millis := (p_bundle ->> 'registeredAtEpochMillis')::bigint;
    v_reader_type := p_bundle ->> 'readerType';
    v_work_id := p_bundle ->> 'workId';
    v_report_id := p_bundle ->> 'reportId';
    v_operator_id := p_bundle ->> 'operatorId';
    v_rule_version := p_bundle ->> 'ruleVersion';
    v_equipment_snapshot_version := p_bundle ->> 'equipmentSnapshotVersion';

    if not exists (
        select 1
        from tracelink_internal.device_registry dr
        where dr.device_id = v_device_id
            and dr.is_enabled
    ) then
        perform tracelink_internal.fn_audit_registration_failure(
            p_bundle,
            'configuration',
            'device_not_assigned',
            'deviceId is not registered or is disabled: ' || v_device_id,
            v_payload_hash
        );

        return query
        select *
        from tracelink_internal.fn_registration_failure(
            'configuration',
            'device_not_assigned',
            'deviceId is not registered or is disabled: ' || v_device_id
        );
        return;
    end if;

    select wc.report_id
    into v_work_report_id
    from tracelink_internal.work_context wc
    where wc.work_id = v_work_id;

    if v_work_report_id is null then
        perform tracelink_internal.fn_audit_registration_failure(
            p_bundle,
            'contract',
            'work_not_found',
            'workId was not found: ' || v_work_id,
            v_payload_hash
        );

        return query
        select *
        from tracelink_internal.fn_registration_failure(
            'contract',
            'work_not_found',
            'workId was not found: ' || v_work_id
        );
        return;
    end if;

    if v_work_report_id <> v_report_id then
        perform tracelink_internal.fn_audit_registration_failure(
            p_bundle,
            'contract',
            'report_mismatch',
            'reportId does not match work context.',
            v_payload_hash
        );

        return query
        select *
        from tracelink_internal.fn_registration_failure(
            'contract',
            'report_mismatch',
            'reportId does not match work context.'
        );
        return;
    end if;

    if not exists (
        select 1
        from tracelink_internal.device_work_assignment dwa
        join tracelink_internal.work_context wc
            on wc.work_id = dwa.work_id
        where dwa.device_id = v_device_id
            and dwa.work_id = v_work_id
            and dwa.is_active
            and wc.status = 'active'
    ) then
        perform tracelink_internal.fn_audit_registration_failure(
            p_bundle,
            'configuration',
            'device_not_assigned',
            'deviceId is not assigned to the submitted active workId.',
            v_payload_hash
        );

        return query
        select *
        from tracelink_internal.fn_registration_failure(
            'configuration',
            'device_not_assigned',
            'deviceId is not assigned to the submitted active workId.'
        );
        return;
    end if;

    if not exists (
        select 1
        from tracelink_internal.rule_bundle rb
        where rb.work_id = v_work_id
            and rb.rule_version = v_rule_version
            and rb.is_active
    ) then
        perform tracelink_internal.fn_audit_registration_failure(
            p_bundle,
            'contract',
            'rule_bundle_not_found',
            'ruleVersion does not match an active rule bundle.',
            v_payload_hash
        );

        return query
        select *
        from tracelink_internal.fn_registration_failure(
            'contract',
            'rule_bundle_not_found',
            'ruleVersion does not match an active rule bundle.'
        );
        return;
    end if;

    select es.snapshot_id
    into v_snapshot_id
    from tracelink_internal.equipment_snapshot es
    where es.work_id = v_work_id
        and es.snapshot_version = v_equipment_snapshot_version
        and es.is_active;

    if v_snapshot_id is null
        or not exists (
            select 1
            from tracelink_internal.equipment_snapshot_record esr
            where esr.snapshot_id = v_snapshot_id
        ) then
        perform tracelink_internal.fn_audit_registration_failure(
            p_bundle,
            'contract',
            'empty_equipment_snapshot',
            'equipmentSnapshotVersion does not match a non-empty active snapshot.',
            v_payload_hash
        );

        return query
        select *
        from tracelink_internal.fn_registration_failure(
            'contract',
            'empty_equipment_snapshot',
            'equipmentSnapshotVersion does not match a non-empty active snapshot.'
        );
        return;
    end if;

    select s.*
    into v_existing
    from tracelink_internal.read_result_session s
    where s.device_id = v_device_id
        and s.session_id = v_session_id;

    if found then
        if v_existing.payload_hash = v_payload_hash then
            insert into tracelink_internal.read_result_audit (
                result_id,
                device_id,
                session_id,
                event_type,
                message,
                payload_hash,
                payload
            )
            values (
                v_existing.result_id,
                v_device_id,
                v_session_id,
                'duplicate_success',
                'Duplicate registration request matched existing payload.',
                v_payload_hash,
                p_bundle
            );

            return query
            select *
            from tracelink_internal.fn_registration_success(
                true,
                v_session_id,
                v_existing.result_id,
                'duplicate registration accepted'
            );
            return;
        end if;

        perform tracelink_internal.fn_audit_registration_failure(
            p_bundle,
            'contract',
            'idempotency_payload_mismatch',
            'Same deviceId/sessionId was registered with a different payload.',
            v_payload_hash,
            v_existing.result_id
        );

        return query
        select *
        from tracelink_internal.fn_registration_failure(
            'contract',
            'idempotency_payload_mismatch',
            'same deviceId/sessionId was already registered with a different payload'
        );
        return;
    end if;

    insert into tracelink_internal.read_result_session as inserted (
        device_id,
        session_id,
        payload_hash,
        payload,
        registered_at_epoch_millis,
        reader_type,
        work_id,
        report_id,
        operator_id,
        rule_version,
        equipment_snapshot_version
    )
    values (
        v_device_id,
        v_session_id,
        v_payload_hash,
        p_bundle,
        v_registered_at_epoch_millis,
        v_reader_type,
        v_work_id,
        v_report_id,
        v_operator_id,
        v_rule_version,
        v_equipment_snapshot_version
    )
    on conflict (device_id, session_id) do nothing
    returning inserted.result_id
    into v_result_id;

    if v_result_id is null then
        -- A concurrent retry inserted the same idempotency key between the earlier check and insert.
        select s.*
        into v_existing
        from tracelink_internal.read_result_session s
        where s.device_id = v_device_id
            and s.session_id = v_session_id;

        if found and v_existing.payload_hash = v_payload_hash then
            insert into tracelink_internal.read_result_audit (
                result_id,
                device_id,
                session_id,
                event_type,
                message,
                payload_hash,
                payload
            )
            values (
                v_existing.result_id,
                v_device_id,
                v_session_id,
                'duplicate_success',
                'Concurrent duplicate registration request matched existing payload.',
                v_payload_hash,
                p_bundle
            );

            return query
            select *
            from tracelink_internal.fn_registration_success(
                true,
                v_session_id,
                v_existing.result_id,
                'duplicate registration accepted'
            );
            return;
        end if;

        perform tracelink_internal.fn_audit_registration_failure(
            p_bundle,
            'contract',
            'idempotency_payload_mismatch',
            'same deviceId/sessionId was concurrently registered with a different payload',
            v_payload_hash,
            v_existing.result_id
        );

        return query
        select *
        from tracelink_internal.fn_registration_failure(
            'contract',
            'idempotency_payload_mismatch',
            'same deviceId/sessionId was concurrently registered with a different payload'
        );
        return;
    end if;

    insert into tracelink_internal.read_result_tag (
        result_id,
        epc,
        first_seen_at_epoch_millis,
        last_seen_at_epoch_millis,
        read_count,
        judgement_status,
        judgement_reason_code
    )
    select
        v_result_id,
        tag.value ->> 'epc',
        (tag.value ->> 'firstSeenAtEpochMillis')::bigint,
        (tag.value ->> 'lastSeenAtEpochMillis')::bigint,
        (tag.value ->> 'readCount')::integer,
        tag.value ->> 'judgementStatus',
        tag.value ->> 'judgementReasonCode'
    from jsonb_array_elements(p_bundle -> 'tags') as tag(value);

    insert into tracelink_internal.read_result_audit (
        result_id,
        device_id,
        session_id,
        event_type,
        message,
        payload_hash,
        payload
    )
    values (
        v_result_id,
        v_device_id,
        v_session_id,
        'registered',
        'Read result registration completed.',
        v_payload_hash,
        p_bundle
    );

    return query
    select *
    from tracelink_internal.fn_registration_success(
        false,
        v_session_id,
        v_result_id,
        'registered'
    );
end;
$$;
