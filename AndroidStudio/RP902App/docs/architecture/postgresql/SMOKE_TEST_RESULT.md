# Smoke Test Result

Date: 2026-04-21

This record follows the current execution priority:

1. SQL smoke tests
2. role hardening refinement
3. debug-only PostgreSQL mode wiring
4. Android PostgreSQL mode smoke tests
5. result recording

## Summary

| area | status | notes |
| --- | --- | --- |
| local PostgreSQL smoke | not executed | `docker` and `psql` are not available in this environment |
| role hardening refinement | completed, not DB-executed | template and validation queries were refined for DBA review |
| debug-only PostgreSQL wiring | completed | fake mode remains default; debug build can opt in with Gradle properties |
| Android postgres-mode smoke | not executed | no emulator/device is attached, and local DB smoke is blocked |
| Android build/unit tests | passed | `testDebugUnitTest` and debug postgres-mode `assembleDebug` passed |

## Step 1: Local PostgreSQL Smoke Execution

Executed checks:

```powershell
Get-Command docker -ErrorAction SilentlyContinue
Get-Command psql -ErrorAction SilentlyContinue
```

Result:

- `docker`: not found
- `psql`: not found

Not executed:

- PostgreSQL container startup
- `pgcrypto` verification
- schema / table / helper / function deployment
- seed data application
- `50_verification_queries.sql`
- `70_role_validation_queries.sql`

Blocker:

The current machine cannot run the local DB smoke plan because neither Docker nor `psql` is installed or available on PATH.

Replay procedure:

Use `VALIDATION_PLAN.md` from a machine with Docker and `psql`.
Start with `compose.postgres-smoke.yml`, then apply:

1. `PostgreSQLContractStubs.sql`
2. `postgresql/40_seed_test_data.sql`
3. `postgresql/50_verification_queries.sql`
4. `postgresql/60_role_hardening_template.sql`
5. `postgresql/70_role_validation_queries.sql`

Expected DB validations still pending:

- `api.fn_get_active_work_context`
- `api.fn_get_rule_bundle`
- `api.fn_get_equipment_snapshot`
- `api.fn_register_read_result_bundle`
- duplicate success
- payload mismatch
- unknown field reject
- empty tags reject
- no work context behavior
- no rule behavior
- empty snapshot behavior
- Android role execute-only path
- internal table direct access denial

## Step 2: Role Hardening Refinement

Completed:

- clarified smoke role names vs production replacements
- made `tracelink_api_owner` a non-login owner role in the template
- made `tracelink_android_app` a login role with no object ownership
- added explicit revoke cleanup for accidental grants to the Android role
- kept Android role grant shape to `api` schema usage and four `api.fn_*` execute grants
- documented DBA review notes for `SECURITY DEFINER`, `public` schema `CREATE`, and `pgcrypto`
- expanded role validation queries for internal table DML denial, helper execute denial, function owner, `SECURITY DEFINER`, and fixed `search_path`

Not executed:

- DBA review
- role creation in a live DB
- privilege validation against PostgreSQL

## Step 3: Debug-Only PostgreSQL Mode Wiring

Completed:

- `MainActivity` now obtains dependencies from `AppContainerFactory`.
- `AppContainerFactory` keeps fake mode as default.
- Debug builds can opt in to PostgreSQL mode with Gradle properties.
- Release builds force `POSTGRES_SMOKE_ENABLED=false`.
- Production credentials are not hardcoded.
- `tracelinkPostgresPassword` is required when debug PostgreSQL smoke mode is enabled.
- `POSTGRES_DEBUG_WIRING.md` documents the command and limitations.

Build checks:

```powershell
.\gradlew.bat testDebugUnitTest
```

Result: passed.

```powershell
& .\gradlew.bat assembleDebug `
  "-PtracelinkPostgresSmoke=true" `
  "-PtracelinkPostgresHost=10.0.2.2" `
  "-PtracelinkPostgresPort=55432" `
  "-PtracelinkPostgresDatabase=tracelink_smoke" `
  "-PtracelinkPostgresUsername=tracelink_android_app" `
  "-PtracelinkPostgresPassword=local_smoke_password" `
  "-PtracelinkPostgresSslMode=Disable" `
  "-PtracelinkPostgresDeviceId=android-local-device" `
  "-PtracelinkPostgresReaderType=RP902"
```

Result: passed.

Note:

An unquoted PowerShell command using `-PtracelinkPostgresHost=10.0.2.2` failed because PowerShell/Gradle treated part of the value as a task name.
The documented commands now quote each `-P...` argument.

## Step 4: Android PostgreSQL Mode Smoke Test

Executed checks:

```powershell
adb devices
```

Result:

- `adb` is available.
- no emulator/device is attached.

Not executed:

- `installDebug` to emulator/device
- app startup in debug PostgreSQL mode
- work context fetch through Android
- rule bundle fetch through Android
- equipment snapshot fetch through Android
- registration success through Android
- duplicate resend through Android
- retryable failure queue behavior
- configuration/contract failure no-queue behavior

Blockers:

- no emulator/device attached
- no local DB container available
- DB-side smoke validation has not passed yet, so Android live smoke would not be meaningful even with a device

## Remaining Issues

- Install Docker or provide a scratch PostgreSQL DB with `psql`.
- Execute SQL smoke and role validation before Android smoke.
- Run DBA review for `60_role_hardening_template.sql`.
- Attach emulator/device and run debug PostgreSQL smoke mode.
- Implement credential storage strategy after smoke validation.
- Implement durable pending write storage after DB and Android smoke pass.

## Recommended Next Order

1. Run the DB smoke plan on a machine with Docker and `psql`.
2. Fix any SQL or privilege issues found by `50_verification_queries.sql` and `70_role_validation_queries.sql`.
3. Have DBA review `60_role_hardening_template.sql`.
4. Install and run debug PostgreSQL mode on emulator using `10.0.2.2:55432`.
5. Repeat on a physical device using a reachable DB host.
6. Only after smoke success, proceed to credential storage and durable pending write implementation.
