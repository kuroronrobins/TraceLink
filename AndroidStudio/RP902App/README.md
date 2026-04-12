# TraceLink RP902 App

TraceLink RP902 App is an Android RFID reader app for the RP902 UHF reader.

## Purpose

The app reads RFID tags from RP902, removes duplicate EPC values inside the current inventory session, shows live results, and prepares a session payload for upload to a backend service.

## App Responsibilities

- Connect to RP902.
- Start and stop tag inventory.
- Remove duplicate EPC values per inventory session.
- Show live inventory results.
- Prepare upload payloads for the backend.
- Keep upload failures visible and retryable.
- Show connection, inventory, upload, and error logs.

## Backend Responsibilities

- Own business rules.
- Compare reads with equipment/master data.
- Persist inventory results.
- Return final judgement or processing result.

## Development Direction

- Kotlin only.
- Jetpack Compose for new UI.
- ViewModel + Repository + unidirectional data flow.
- Keep RP902 vendor SDK code isolated behind an adapter interface.
- Use an interface plus fake implementation until actual SDK details are known.
- Validate final RP902 behavior on a real Android device.

## Reader Settings

The app still starts in fake reader mode by default. For the current one-reader operation, the RP902 Bluetooth MAC field is prefilled with `DC:0D:30:DA:0F:3C` so hardware testing does not require typing the address every time.

This is only a default value. The Settings screen remains editable, and the MAC address can be changed later if a different RP902 reader is used.

## Vendor SDK Assets

Unitech SDK files are stored under `vendor/unitech/` with separate roles:

- `vendor/unitech/runtime/`: stable build inputs used by `app/build.gradle.kts`.
- `vendor/unitech/docs/`: Javadoc and vendor reference documents.
- `vendor/unitech/upstream/`: preserved original SDK distribution for traceability.

The app must only depend on `vendor/unitech/runtime/`. Sample apps, APKs, Xamarin files, Gradle wrappers, and sample signing material remain isolated under `upstream/` and are not part of the app dependency path.

Before updating or redistributing vendor files, confirm the Unitech license terms. For SDK updates, replace the upstream drop, copy only required runtime binaries into `runtime/`, update docs if file names or API findings change, then run build, unit tests, and lint.

## Maintenance Notes

Operational risk notes are kept under `docs/maintenance/`.

- `docs/maintenance/16kb_alignment_assessment.md`: current `Aligned16KB` lint warning and vendor native library risk.
- `docs/maintenance/signing_and_keystore_audit.md`: app signing path and confirmation that sample keystores are not used.
- `docs/maintenance/in_memory_inventory.md`: in-memory stores that must be revisited before production.
- `docs/maintenance/runtime_risks_summary.md`: prioritized summary of runtime and release risks.
