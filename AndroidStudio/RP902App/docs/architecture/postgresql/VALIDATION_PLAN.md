# PostgreSQL Smoke Test And Validation Plan

この文書は Phase 6 の実行基準である。
目的は Android ↔ PostgreSQL 契約を変えず、現行 SQL 実装を real DB または local container で安全に検証できる状態にすること。

## Validation Scope Inventory

| category | validation target | files / code | pass condition |
| --- | --- | --- | --- |
| DB schema / function deployment | `api` と `tracelink_internal` が作成され、`api.fn_*` が compile される | `00_schema.sql` to `30_api_functions.sql` | `psql -v ON_ERROR_STOP=1` で error なく適用できる |
| `pgcrypto` assumption | `digest()` と UUID 生成が利用できる | `00_schema.sql`, `20_helpers.sql` | `create extension pgcrypto` が成功し、registration が payload hash を作れる |
| internal table / constraint / idempotency | 内部 table、unique key、check constraint、audit が機能する | `10_internal_tables.sql`, `30_api_functions.sql` | duplicate success と payload mismatch が期待通り分岐する |
| `api.fn_*` behavior | Android-facing function の row shape と cardinality | `30_api_functions.sql` | normal case と failure case が contract 通り |
| role / grant / revoke | Android role が `api.fn_*` だけ実行できる | `60_role_hardening_template.sql`, `70_role_validation_queries.sql` | internal table direct read が不可、function execute は可 |
| SECURITY DEFINER hardening | definer owner、search_path、public CREATE revoke | `30_api_functions.sql`, `60_role_hardening_template.sql` | `api.fn_*` は `SECURITY DEFINER`、固定 `search_path`、public CREATE revoked |
| Android postgres mode connectivity | `JdbcPostgresGateway` が real DB に接続できる | `AppContainer`, `PostgresConnectionSettings` | work/rule/equipment fetch と registration が app から通る |
| Android registration behavior | local judgement 後の result registration | `DefaultInventoryRepository`, `PostgresReadResultRepository` | success は completed、retryable は queue、contract/configuration は queue しない |
| duplicate / retry / mismatch handling | idempotency と pending write semantics | DB function + Kotlin error/result mapping | duplicate success は完了扱い、payload mismatch は contract failure |
| seed / verification quality | 初回実行者が期待結果を追える | `40_seed_test_data.sql`, `50_verification_queries.sql` | case ごとの expected result が明確 |

## Prerequisites

### Local Container Option

- Docker Desktop or Docker Engine
- `psql` client
- repository root: `C:\Users\kuroron\Documents\RD\20260405_RFIDXcconnect`

Local container は SSL なしで起動するため、Android smoke test では `PostgresSslMode.Disable` を使う。
production では `VerifyCa` または `VerifyFull` を検討する。

### Real DB Option

- PostgreSQL 15+ or 16+ recommended
- DB admin role
- `pgcrypto` extension を作成できる権限
- Android 用 DB role を作れる権限
- Android 端末または emulator から DB host / port へ到達できる network path

## Files To Apply

| order | file | required | purpose |
| --- | --- | --- | --- |
| 1 | `PostgreSQLContractStubs.sql` | yes | implementation runner |
| 2 | `postgresql/40_seed_test_data.sql` | smoke only | deterministic test data |
| 3 | `postgresql/50_verification_queries.sql` | smoke only | contract behavior checks |
| 4 | `postgresql/60_role_hardening_template.sql` | role smoke / production template | least-privilege grants and owner setup |
| 5 | `postgresql/70_role_validation_queries.sql` | role smoke | privilege checks under Android role |

## Local Container Execution

Run from repository root.

```powershell
docker compose -f AndroidStudio/RP902App/docs/architecture/postgresql/compose.postgres-smoke.yml up -d
$env:PGPASSWORD = "tracelink_smoke_password"
psql -h localhost -p 55432 -U tracelink_admin -d tracelink_smoke -c "select version();"
```

Expected:

- container health becomes healthy
- `select version()` returns PostgreSQL version

Apply the implementation:

```powershell
Set-Location AndroidStudio/RP902App/docs/architecture
psql -h localhost -p 55432 -U tracelink_admin -d tracelink_smoke -v ON_ERROR_STOP=1 -f PostgreSQLContractStubs.sql
psql -h localhost -p 55432 -U tracelink_admin -d tracelink_smoke -v ON_ERROR_STOP=1 -f postgresql/40_seed_test_data.sql
psql -h localhost -p 55432 -U tracelink_admin -d tracelink_smoke -v ON_ERROR_STOP=1 -f postgresql/50_verification_queries.sql
```

Expected:

- implementation SQL applies without error
- seed SQL commits
- verification query outputs match the table in `Expected DB Results`

Apply role hardening:

```powershell
psql -h localhost -p 55432 -U tracelink_admin -d tracelink_smoke -v ON_ERROR_STOP=1 -f postgresql/60_role_hardening_template.sql
psql -h localhost -p 55432 -U tracelink_admin -d tracelink_smoke -v ON_ERROR_STOP=1 -c "alter role tracelink_android_app with password 'tracelink_android_smoke_password';"
psql -h localhost -p 55432 -U tracelink_admin -d tracelink_smoke -v ON_ERROR_STOP=1 -f postgresql/70_role_validation_queries.sql
```

Expected:

- every `passed` column in privilege checks is `true`
- `set role tracelink_android_app` can call `api.fn_*`
- commented direct table read fails with permission denied if executed manually

Cleanup:

```powershell
Set-Location C:\Users\kuroron\Documents\RD\20260405_RFIDXcconnect
docker compose -f AndroidStudio/RP902App/docs/architecture/postgresql/compose.postgres-smoke.yml down -v
```

## Real DB Execution

1. Create or select a scratch database.
2. Confirm `pgcrypto` can be created.
3. Apply `PostgreSQLContractStubs.sql`.
4. Apply `40_seed_test_data.sql`.
5. Run `50_verification_queries.sql`.
6. Review `60_role_hardening_template.sql` with the DBA.
7. Replace role names if needed.
8. Apply role hardening.
9. Set Android role authentication outside source control.
10. Run `70_role_validation_queries.sql`.

Expected:

- no Android-facing contract changes are needed
- Android role can execute only `api.fn_*`
- internal table direct read is denied

## Expected DB Results

| case | input / action | expected DB result | Kotlin-side expectation |
| --- | --- | --- | --- |
| work context normal | `fn_get_active_work_context('android-local-device')` | one row: `work-1`, `report-1`, `operator-1` | `WorkContext` fetched |
| rule bundle normal | `fn_get_rule_bundle('work-1')` | one row: `rule-v1`, `report-1`, non-empty target types | `RuleBundle` fetched |
| equipment snapshot normal | `fn_get_equipment_snapshot('work-1')` | one or more rows; ordering is not relevant | `EquipmentSnapshot` fetched |
| first registration | `verification-session-1` payload | `success=true`, `duplicate=false`, `result_id` not null | registration completed |
| duplicate success | same payload again | `success=true`, `duplicate=true`, same accepted session | completed, pending item removable |
| payload mismatch | same device/session with changed payload | `success=false`, `failure_kind='contract'`, `error_code='idempotency_payload_mismatch'` | `RegistrationFailureKind.Contract`, not queued |
| unknown top-level field | payload with `unexpectedField` | `success=false`, `contract`, `unknown_field` | contract failure, not queued |
| unknown tag field | add unexpected field under a tag | `success=false`, `contract`, `unknown_field` | contract failure, not queued |
| missing required field | remove `ruleVersion` or required tag field | `success=false`, `contract`, `missing_required_field` | contract failure, not queued |
| invalid schemaVersion | set `schemaVersion=2` | `success=false`, `contract`, `invalid_schema_version` | contract failure, not queued |
| invalid judgementStatus | use value outside `Accepted`, `Excluded`, `Ng` | `success=false`, `contract`, `invalid_judgement_status` | contract failure, not queued |
| empty tags | `tags=[]` | `success=false`, `contract`, `empty_tags` | contract failure, not queued |
| invalid time range | `lastSeenAtEpochMillis < firstSeenAtEpochMillis` | `success=false`, `contract`, `invalid_time_range` | contract failure, not queued |
| device not assigned | unregistered/disabled/unassigned device | `success=false`, `configuration`, `device_not_assigned` for registration; getter raises `P0002` | configuration failure, not queued |
| work not found | unknown `workId` | `success=false`, `contract`, `work_not_found` | contract failure, not queued |
| report mismatch | payload `reportId` differs from work context | `success=false`, `contract`, `report_mismatch` | contract failure, not queued |
| rule bundle missing | payload `ruleVersion` not active | `success=false`, `contract`, `rule_bundle_not_found` | contract failure, not queued |
| empty equipment snapshot | payload snapshot missing or active snapshot has no records | `success=false`, `contract`, `empty_equipment_snapshot` | contract failure, not queued |
| internal DB exception | stop DB, revoke required owner privilege, or corrupt function | SQL exception | `PostgresErrorMapper` maps to retryable/configuration/contract/unknown by SQLSTATE |

## DB Function Scenario Matrix

### `api.fn_get_active_work_context`

| scenario | setup | expected |
| --- | --- | --- |
| normal | seed data | exactly 1 row |
| device not assigned | call with `unassigned-device` | SQL exception `P0002`, message starts with `device_not_assigned` |
| multiple active work contexts | destructive scratch only: remove/disable `uq_device_active_work_assignment`, insert two active assignments | SQL exception `P0003`, message starts with `multiple_active_work_contexts` |
| invalid deviceId | call with `null` or blank text | SQL exception `P0002` |
| restricted role execution | run via `set role tracelink_android_app` | function succeeds without internal table grants |

Note: normal schema prevents multiple active contexts by unique partial index. The function branch is defensive and should only be exercised in a disposable DB.

### `api.fn_get_rule_bundle`

| scenario | setup | expected |
| --- | --- | --- |
| normal | seed data | exactly 1 row |
| rule missing | call missing work or deactivate rule | SQL exception `P0002`, message starts with `rule_bundle_not_found` |
| empty `target_equipment_types` | attempt insert empty array | check constraint rejects row before API call |
| report consistency | attempt rule row with mismatched `(work_id, report_id)` | composite FK rejects row |

### `api.fn_get_equipment_snapshot`

| scenario | setup | expected |
| --- | --- | --- |
| normal | seed data | 1+ rows |
| empty snapshot | create scratch work with active snapshot and no records | SQL exception `P0002`, message starts with `empty_equipment_snapshot` |
| metadata consistency | inspect output for same `snapshot_version` / `captured_at_epoch_millis` | all rows have same metadata |
| row-order independence | run query repeatedly without `order by` | Android must still aggregate by metadata and records |

### `api.fn_register_read_result_bundle`

| scenario | expected |
| --- | --- |
| normal registration | one success row, `duplicate=false` |
| duplicate success | one success row, `duplicate=true` |
| payload mismatch | one failure row, `contract/idempotency_payload_mismatch` |
| unknown top-level field | one failure row, `contract/unknown_field` |
| unknown tag field | one failure row, `contract/unknown_field` |
| missing required field | one failure row, `contract/missing_required_field` |
| invalid schemaVersion | one failure row, `contract/invalid_schema_version` |
| invalid judgementStatus | one failure row, `contract/invalid_judgement_status` |
| empty tags | one failure row, `contract/empty_tags` |
| invalid time range | one failure row, `contract/invalid_time_range` |
| device not assigned | one failure row, `configuration/device_not_assigned` |
| work not found | one failure row, `contract/work_not_found` |
| report mismatch | one failure row, `contract/report_mismatch` |
| rule bundle missing | one failure row, `contract/rule_bundle_not_found` |
| empty equipment snapshot | one failure row, `contract/empty_equipment_snapshot` |
| internal DB exception behavior | SQL exception; do not convert to success row |

## Role / Grant / Revoke Validation Points

| check | expected |
| --- | --- |
| Android role has `USAGE` on `api` | true |
| Android role has `EXECUTE` on all four `api.fn_*` | true |
| Android role has `USAGE` on `tracelink_internal` | false |
| Android role can `SELECT` internal tables | false |
| public has `CREATE` on `public` schema | false |
| public has execute on `tracelink_internal` helpers | false |
| `api.fn_*` owner | specialized owner role, not Android login |
| `api.fn_*` `SECURITY DEFINER` | true |
| `api.fn_*` `search_path` | fixed, not caller-controlled |

## SECURITY DEFINER Hardening Checklist

- `api.fn_*` uses explicit `set search_path`.
- Android role is not the function owner.
- Function owner is a specialized non-login or non-app role.
- Android role has no direct privileges on `tracelink_internal`.
- `public` schema `CREATE` is revoked from public.
- Internal helper functions are not executable by public.
- Internal table references in SQL are schema-qualified.
- Dynamic SQL is not used in Android-facing functions.
- `pgcrypto` location is known; if installed in `public`, `public CREATE` must stay revoked.
- Extension installation is controlled by DBA or migration owner.
- `SECURITY DEFINER` is used only for `api.fn_*`, where least-privilege execution requires it.
- Production deployment records the owner role and grants in change management.

## Android PostgreSQL Mode Smoke Test

Current state:

- `AppContainer` supports `DataAccessMode.Postgres`.
- `MainActivity` still constructs `AppContainer()` with fake data access by default.
- There is no runtime UI switch for PostgreSQL mode yet.

Therefore, Android live smoke requires a temporary debug-only wiring or an instrumentation harness that constructs:

```kotlin
AppContainer(
    dataAccessMode = DataAccessMode.Postgres,
    postgresConnectionSettings = PostgresConnectionSettings(
        host = "<db-host>",
        port = 55432,
        databaseName = "tracelink_smoke",
        username = "tracelink_android_app",
        password = "<not-in-source-control>",
        sslMode = PostgresSslMode.Disable,
    ),
    registrationEnvironment = RegistrationEnvironment(
        deviceId = "android-local-device",
        readerType = "RP902",
    ),
)
```

Use `10.0.2.2` from Android emulator to reach a host-local Docker container.
Use the host machine LAN IP or VPN-routable DB address from a physical Android device.

### Android Steps

| step | action | expected |
| --- | --- | --- |
| 1 | prepare DB and role validation | DB checks pass before app test |
| 2 | run app with `DataAccessMode.Postgres` debug wiring | app starts without DI exception |
| 3 | open Inventory with fake reader mode | no real RP902 dependency needed |
| 4 | connect fake reader | reader connected state |
| 5 | start inventory and emit seeded EPCs | session accumulates normalized EPCs |
| 6 | stop/register session | work context, rule bundle, equipment snapshot are fetched from DB |
| 7 | successful registration | `read_result_session` row exists; UI reaches completed state |
| 8 | resend same bundle or rerun pending write with same payload | DB returns duplicate success; UI treats as completed |
| 9 | force DB offline before registration | `RegistrationFailureKind.Retryable`; pending write is queued |
| 10 | use disabled/unassigned deviceId | `RegistrationFailureKind.Configuration`; pending write is not queued |
| 11 | use mismatched ruleVersion/reportId payload in a controlled test harness | `RegistrationFailureKind.Contract`; pending write is not queued |
| 12 | inspect app logs | fetch/register/failure checkpoints are visible |

Unknowns:

- Credential storage is not implemented.
- Durable pending write storage is not implemented; current queue is in-memory.
- SSL certificate provisioning for managed Android devices is not implemented.
- Runtime UI switch for PostgreSQL mode is not implemented.

## Failure Triage

| symptom | likely area | first place to look |
| --- | --- | --- |
| SQL file fails to apply | SQL syntax / extension privilege | psql error line, `00_schema.sql`, `20_helpers.sql` |
| `digest` missing | extension schema / pgcrypto | `create extension pgcrypto`, function `fn_payload_hash` |
| API functions work as admin but not Android role | grants / SECURITY DEFINER owner | `60_role_hardening_template.sql`, `70_role_validation_queries.sql` |
| Android cannot connect | network / SSL / credentials | `PostgresConnectionSettings`, DB listener, firewall, VPN |
| Android maps DB failure to unknown | SQLSTATE classification | `PostgresErrorMapper` and exception SQLSTATE |
| duplicate retry creates new row | idempotency key not matching | `deviceId`, `sessionId`, `read_result_session` unique index |
| payload mismatch treated as success | payload hash / conflict branch | `fn_register_read_result_bundle`, audit rows |
| contract failure is queued | Kotlin registration handling | `DefaultInventoryRepository`, `RegistrationFailureKind` |

## Recommended Execution Order

1. Run local container DB deployment.
2. Run seed and verification queries as admin.
3. Apply role hardening template.
4. Run role validation queries.
5. Repeat against real scratch DB.
6. Perform Android emulator smoke with local container.
7. Perform Android physical device smoke on the development network.
8. Capture unresolved runtime/network/credential issues before production DB work.
