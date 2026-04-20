-- TraceLink PostgreSQL contract implementation: internal tables.
-- Public Android access remains limited to api.fn_*.

create table if not exists tracelink_internal.device_registry (
    device_id text primary key,
    display_name text not null default '',
    is_enabled boolean not null default true,
    created_at_epoch_millis bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint),
    updated_at_epoch_millis bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint),
    constraint device_registry_device_id_nonblank check (btrim(device_id) <> '')
);

create table if not exists tracelink_internal.work_context (
    work_id text primary key,
    report_id text not null,
    operator_id text,
    started_at_epoch_millis bigint not null,
    status text not null default 'active',
    created_at_epoch_millis bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint),
    updated_at_epoch_millis bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint),
    constraint work_context_work_id_nonblank check (btrim(work_id) <> ''),
    constraint work_context_report_id_nonblank check (btrim(report_id) <> ''),
    constraint work_context_started_nonnegative check (started_at_epoch_millis >= 0),
    constraint work_context_status_known check (status in ('active', 'closed'))
);

create unique index if not exists uq_work_context_work_report
    on tracelink_internal.work_context (work_id, report_id);

create table if not exists tracelink_internal.device_work_assignment (
    assignment_id uuid primary key default gen_random_uuid(),
    device_id text not null references tracelink_internal.device_registry (device_id),
    work_id text not null references tracelink_internal.work_context (work_id),
    is_active boolean not null default true,
    assigned_at_epoch_millis bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint),
    unassigned_at_epoch_millis bigint,
    created_at_epoch_millis bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint),
    updated_at_epoch_millis bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint),
    constraint device_work_assignment_time_order check (
        unassigned_at_epoch_millis is null
        or unassigned_at_epoch_millis >= assigned_at_epoch_millis
    )
);

-- The API function still checks for multiple rows so broken imports fail loudly.
create unique index if not exists uq_device_active_work_assignment
    on tracelink_internal.device_work_assignment (device_id)
    where is_active;

create table if not exists tracelink_internal.rule_bundle (
    rule_bundle_id uuid primary key default gen_random_uuid(),
    work_id text not null,
    report_id text not null,
    rule_version text not null,
    effective_at_epoch_millis bigint not null,
    target_equipment_types text[] not null,
    is_active boolean not null default true,
    created_at_epoch_millis bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint),
    updated_at_epoch_millis bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint),
    constraint rule_bundle_work_report_fk foreign key (work_id, report_id)
        references tracelink_internal.work_context (work_id, report_id),
    constraint rule_bundle_version_nonblank check (btrim(rule_version) <> ''),
    constraint rule_bundle_effective_nonnegative check (effective_at_epoch_millis >= 0),
    constraint rule_bundle_target_types_not_empty check (cardinality(target_equipment_types) > 0),
    constraint rule_bundle_target_types_nonblank check (
        array_position(target_equipment_types, '') is null
    )
);

create unique index if not exists uq_rule_bundle_work_version
    on tracelink_internal.rule_bundle (work_id, rule_version);

create unique index if not exists uq_rule_bundle_active_work
    on tracelink_internal.rule_bundle (work_id)
    where is_active;

create table if not exists tracelink_internal.equipment_snapshot (
    snapshot_id uuid primary key default gen_random_uuid(),
    work_id text not null references tracelink_internal.work_context (work_id),
    snapshot_version text not null,
    captured_at_epoch_millis bigint not null,
    is_active boolean not null default true,
    created_at_epoch_millis bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint),
    updated_at_epoch_millis bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint),
    constraint equipment_snapshot_version_nonblank check (btrim(snapshot_version) <> ''),
    constraint equipment_snapshot_captured_nonnegative check (captured_at_epoch_millis >= 0)
);

create unique index if not exists uq_equipment_snapshot_work_version
    on tracelink_internal.equipment_snapshot (work_id, snapshot_version);

create unique index if not exists uq_equipment_snapshot_active_work
    on tracelink_internal.equipment_snapshot (work_id)
    where is_active;

create table if not exists tracelink_internal.equipment_snapshot_record (
    snapshot_id uuid not null references tracelink_internal.equipment_snapshot (snapshot_id) on delete cascade,
    equipment_id text not null,
    epc text not null,
    equipment_type text not null,
    display_name text not null,
    created_at_epoch_millis bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint),
    primary key (snapshot_id, equipment_id),
    constraint equipment_record_equipment_id_nonblank check (btrim(equipment_id) <> ''),
    constraint equipment_record_epc_nonblank check (btrim(epc) <> ''),
    constraint equipment_record_epc_normalized check (epc = upper(btrim(epc))),
    constraint equipment_record_type_nonblank check (btrim(equipment_type) <> ''),
    constraint equipment_record_display_name_nonblank check (btrim(display_name) <> '')
);

create unique index if not exists uq_equipment_snapshot_record_epc
    on tracelink_internal.equipment_snapshot_record (snapshot_id, epc);

create table if not exists tracelink_internal.read_result_session (
    result_id uuid primary key default gen_random_uuid(),
    device_id text not null references tracelink_internal.device_registry (device_id),
    session_id text not null,
    payload_hash bytea not null,
    payload jsonb not null,
    registered_at_epoch_millis bigint not null,
    reader_type text not null,
    work_id text not null,
    report_id text not null,
    operator_id text,
    rule_version text not null,
    equipment_snapshot_version text not null,
    created_at_epoch_millis bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint),
    updated_at_epoch_millis bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint),
    constraint read_result_session_device_id_nonblank check (btrim(device_id) <> ''),
    constraint read_result_session_session_id_nonblank check (btrim(session_id) <> ''),
    constraint read_result_session_payload_object check (jsonb_typeof(payload) = 'object'),
    constraint read_result_session_registered_nonnegative check (registered_at_epoch_millis >= 0),
    constraint read_result_session_reader_type_nonblank check (btrim(reader_type) <> ''),
    constraint read_result_session_report_id_nonblank check (btrim(report_id) <> ''),
    constraint read_result_session_rule_version_nonblank check (btrim(rule_version) <> ''),
    constraint read_result_session_snapshot_version_nonblank check (btrim(equipment_snapshot_version) <> ''),
    constraint read_result_session_work_report_fk foreign key (work_id, report_id)
        references tracelink_internal.work_context (work_id, report_id)
);

-- This is the DB-side idempotency key used by Android PendingWriteQueue.
create unique index if not exists uq_read_result_device_session
    on tracelink_internal.read_result_session (device_id, session_id);

create table if not exists tracelink_internal.read_result_tag (
    result_id uuid not null references tracelink_internal.read_result_session (result_id) on delete cascade,
    epc text not null,
    first_seen_at_epoch_millis bigint not null,
    last_seen_at_epoch_millis bigint not null,
    read_count integer not null,
    judgement_status text not null,
    judgement_reason_code text,
    created_at_epoch_millis bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint),
    primary key (result_id, epc),
    constraint read_result_tag_epc_nonblank check (btrim(epc) <> ''),
    constraint read_result_tag_epc_normalized check (epc = upper(btrim(epc))),
    constraint read_result_tag_time_order check (last_seen_at_epoch_millis >= first_seen_at_epoch_millis),
    constraint read_result_tag_read_count_positive check (read_count >= 1),
    constraint read_result_tag_status_known check (judgement_status in ('Accepted', 'Excluded', 'Ng')),
    constraint read_result_tag_reason_consistent check (
        (judgement_status = 'Accepted' and judgement_reason_code is null)
        or (judgement_status <> 'Accepted' and judgement_reason_code is not null and btrim(judgement_reason_code) <> '')
    )
);

create table if not exists tracelink_internal.read_result_audit (
    audit_id bigint generated always as identity primary key,
    result_id uuid,
    device_id text,
    session_id text,
    event_type text not null,
    failure_kind text,
    error_code text,
    message text,
    payload_hash bytea,
    payload jsonb,
    created_at_epoch_millis bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint),
    constraint read_result_audit_event_type_known check (
        event_type in (
            'registered',
            'duplicate_success',
            'contract_failure',
            'configuration_failure',
            'retryable_failure',
            'unknown_failure'
        )
    ),
    constraint read_result_audit_failure_kind_known check (
        failure_kind is null
        or failure_kind in ('retryable', 'configuration', 'contract', 'unknown')
    )
);
