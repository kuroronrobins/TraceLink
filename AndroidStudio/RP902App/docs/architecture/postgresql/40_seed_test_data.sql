-- TraceLink PostgreSQL contract implementation: seed data for manual verification.
-- These rows are intentionally small and deterministic so DB implementers can rerun verification scripts.

begin;

insert into tracelink_internal.device_registry (
    device_id,
    display_name,
    is_enabled,
    updated_at_epoch_millis
)
values (
    'android-local-device',
    'Android local verification device',
    true,
    tracelink_internal.fn_epoch_millis()
)
on conflict (device_id) do update
set
    display_name = excluded.display_name,
    is_enabled = excluded.is_enabled,
    updated_at_epoch_millis = excluded.updated_at_epoch_millis;

insert into tracelink_internal.work_context (
    work_id,
    report_id,
    operator_id,
    started_at_epoch_millis,
    status,
    updated_at_epoch_millis
)
values (
    'work-1',
    'report-1',
    'operator-1',
    1712300000000,
    'active',
    tracelink_internal.fn_epoch_millis()
)
on conflict (work_id) do update
set
    report_id = excluded.report_id,
    operator_id = excluded.operator_id,
    started_at_epoch_millis = excluded.started_at_epoch_millis,
    status = excluded.status,
    updated_at_epoch_millis = excluded.updated_at_epoch_millis;

-- Keep the active assignment invariant explicit before inserting the verification assignment.
update tracelink_internal.device_work_assignment
set
    is_active = false,
    unassigned_at_epoch_millis = tracelink_internal.fn_epoch_millis(),
    updated_at_epoch_millis = tracelink_internal.fn_epoch_millis()
where device_id = 'android-local-device'
    and is_active;

insert into tracelink_internal.device_work_assignment (
    device_id,
    work_id,
    is_active
)
values (
    'android-local-device',
    'work-1',
    true
);

update tracelink_internal.rule_bundle
set
    is_active = false,
    updated_at_epoch_millis = tracelink_internal.fn_epoch_millis()
where work_id = 'work-1'
    and rule_version <> 'rule-v1'
    and is_active;

insert into tracelink_internal.rule_bundle (
    work_id,
    report_id,
    rule_version,
    effective_at_epoch_millis,
    target_equipment_types,
    is_active,
    updated_at_epoch_millis
)
values (
    'work-1',
    'report-1',
    'rule-v1',
    1712300000000,
    array['traceable-equipment']::text[],
    true,
    tracelink_internal.fn_epoch_millis()
)
on conflict (work_id, rule_version) do update
set
    report_id = excluded.report_id,
    effective_at_epoch_millis = excluded.effective_at_epoch_millis,
    target_equipment_types = excluded.target_equipment_types,
    is_active = excluded.is_active,
    updated_at_epoch_millis = excluded.updated_at_epoch_millis;

update tracelink_internal.equipment_snapshot
set
    is_active = false,
    updated_at_epoch_millis = tracelink_internal.fn_epoch_millis()
where work_id = 'work-1'
    and snapshot_version <> 'equipment-v1'
    and is_active;

with upserted_snapshot as (
    insert into tracelink_internal.equipment_snapshot (
        work_id,
        snapshot_version,
        captured_at_epoch_millis,
        is_active,
        updated_at_epoch_millis
    )
    values (
        'work-1',
        'equipment-v1',
        1712300000000,
        true,
        tracelink_internal.fn_epoch_millis()
    )
    on conflict (work_id, snapshot_version) do update
    set
        captured_at_epoch_millis = excluded.captured_at_epoch_millis,
        is_active = excluded.is_active,
        updated_at_epoch_millis = excluded.updated_at_epoch_millis
    returning snapshot_id
)
insert into tracelink_internal.equipment_snapshot_record (
    snapshot_id,
    equipment_id,
    epc,
    equipment_type,
    display_name
)
select
    upserted_snapshot.snapshot_id,
    seed.equipment_id,
    seed.epc,
    seed.equipment_type,
    seed.display_name
from upserted_snapshot
cross join (
    values
        ('equipment-1', 'E2806894000040035A1F90A1', 'traceable-equipment', 'Sample Pump A'),
        ('equipment-2', 'E2806894000040035A1F90A2', 'traceable-equipment', 'Sample Pump B')
) as seed(equipment_id, epc, equipment_type, display_name)
on conflict (snapshot_id, equipment_id) do update
set
    epc = excluded.epc,
    equipment_type = excluded.equipment_type,
    display_name = excluded.display_name;

delete from tracelink_internal.equipment_snapshot_record
where snapshot_id = (
        select snapshot_id
        from tracelink_internal.equipment_snapshot
        where work_id = 'work-1'
            and snapshot_version = 'equipment-v1'
    )
    and equipment_id not in ('equipment-1', 'equipment-2');

commit;
