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

## Vendor SDK Assets

Unitech SDK files are stored under `vendor/unitech/` with separate roles:

- `vendor/unitech/runtime/`: stable build inputs used by `app/build.gradle.kts`.
- `vendor/unitech/docs/`: Javadoc and vendor reference documents.
- `vendor/unitech/upstream/`: preserved original SDK distribution for traceability.

The app must only depend on `vendor/unitech/runtime/`. Sample apps, APKs, Xamarin files, Gradle wrappers, and sample signing material remain isolated under `upstream/` and are not part of the app dependency path.

Before updating or redistributing vendor files, confirm the Unitech license terms. For SDK updates, replace the upstream drop, copy only required runtime binaries into `runtime/`, update docs if file names or API findings change, then run build, unit tests, and lint.
