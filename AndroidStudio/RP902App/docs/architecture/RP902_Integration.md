# RP902 Integration

## Policy

RP902 vendor SDK code must stay outside composables and ViewModels. UI and ViewModels depend on app-owned contracts, not directly on vendor classes.

## Target Structure

- UI layer
- ViewModel
- Repository / workflow layer
- ReaderGateway interface
- RP902 real implementation
- RP902 fake implementation
- PostgreSQL-facing repositories behind app-owned contracts

## Package Structure

The implementation remains in the single `:app` module and separates packages by layer:

- `domain/reader`: app-owned reader gateway contract and reader state models.
- `domain/inventory`: session and duplicate-filtering logic plus inventory repository contract.
- `domain/work`: work context contract.
- `domain/rule`: rule bundle contract.
- `domain/equipment`: equipment snapshot contract.
- `domain/judgement`: local judgement service contract.
- `domain/readresult`: read result registration models, repository contract, pending write queue, registration state.
- `domain/database`: PostgreSQL View / Function gateway boundary.
- `domain/log`: structured app log models and log store contract.
- `data/reader`: fake/real reader gateway implementations and switching.
- `data/inventory`: default inventory workflow orchestration.
- `data/work`: fake work context repository.
- `data/rule`: fake rule repository.
- `data/equipment`: fake equipment master repository.
- `data/judgement`: minimal local judgement implementation.
- `data/readresult`: read result bundle factory, fake read result repository, and in-memory pending write queue.
- `data/postgres`: JDBC `PostgresGateway`, View / Function names, row mappers, JSONB argument conversion, error mapping, and repository adapters.
- `data/log`: in-memory event log store.
- `ui/app`: app shell and route selection.
- `ui/inventory`: inventory screen state projection and Compose UI.
- `ui/logs`: event log screen.

The ViewModel exposes UI state as a projection of repository state. Composables receive state and callbacks only; they do not call reader gateways or PostgreSQL-facing repositories directly.

`DefaultInventoryRepository` remains the UI-facing workflow facade. It subscribes to reader events, updates repository state, aggregates logs, and runs the registration/retry order. Work context retrieval, rule retrieval, equipment snapshot retrieval, local judgement, and registration bundle construction are injected responsibilities.

## Current Stage

The app still defaults to `FakeReaderGateway`. The Unitech AAR/JAR are added to the app as local file dependencies so `RealRp902Gateway` can compile against confirmed vendor types. The real gateway remains behind the app-owned `ReaderGateway` contract and is only selected when reader settings are changed from fake to real.

Data access also defaults to fake mode. `AppContainer` can be constructed with `DataAccessMode.Postgres` and `PostgresConnectionSettings` to wire `PostgresWorkContextRepository`, `PostgresRuleRepository`, `PostgresEquipmentMasterRepository`, and `PostgresReadResultRepository` to `JdbcPostgresGateway`.

`JdbcPostgresGateway` uses pgJDBC and opens a short-lived JDBC connection per operation on `Dispatchers.IO`. SQL text, schema names, function names, row mapping, JSONB registration payload conversion, and PostgreSQL error classification stay inside `data.postgres`. UI, ViewModel, `DefaultInventoryRepository`, `InventorySession`, and `ReadJudgementService` do not contain SQL or table names.

The PostgreSQL path has compile, unit-test, and debug APK build coverage. Live DB verification procedures are defined in `postgresql/VALIDATION_PLAN.md`; a real Android postgres-mode smoke test still requires debug-only wiring because `MainActivity` defaults to fake data access.

`AppContainer` is the switch point:

- `ReaderGatewayMode.Fake`: default, local development path.
- `ReaderGatewayMode.RealRp902`: explicit opt-in path for hardware preparation.

`ConfigurableReaderGateway` listens to app-owned reader settings and swaps the active gateway between fake and real implementations. Switching modes disconnects the previous gateway first. For the real RP902 path, it also checks app-owned runtime preflight state before delegating to the vendor-backed gateway. This keeps composables and ViewModels free from vendor SDK calls.

## Confirmed Vendor SDK Surface

The following items were confirmed from Unitech SDK 1.0.41 Javadoc and the bundled sample source preserved in `vendor/unitech/upstream/`. This section records observed API names only; it is not an implementation plan for unchecked behavior.

### Reader And Transport

- `TransportBluetooth(DeviceType.RP902, "RP902", bluetoothMacAddress)`.
- `RP902Reader(transport)`.
- `reader.addListener(IReaderEventListener)`.
- `reader.connect()`.
- `reader.disconnect()`.
- `reader.clearListener()`.

### Inventory

- `reader.getRfidUhf().addListener(IRfidUhfEventListener)`.
- `reader.getRfidUhf().inventory6c()`.
- `reader.getRfidUhf().stop()`.
- `reader.getRfidUhf().removeListener(...)`.

## Reader Settings And Preflight

The app has app-owned reader settings:

- `ReaderSettings`: selected gateway mode and optional normalized Bluetooth MAC address.
- `ReaderBluetoothAddress`: pure parser/normalizer for `00:11:22:33:44:55` and `001122334455` formats.
- `ReaderConnectionPreflight`: pure helper for required runtime permissions and connection readiness checks.
- `ReaderRuntimeState`: app-owned runtime snapshot of granted reader permissions and Bluetooth adapter state.
- `AndroidReaderPermissionMapper`: Android permission string mapping kept outside the domain layer.

The settings screen can switch between fake and real gateway modes, edit the RP902 Bluetooth MAC address, request runtime permissions, refresh Bluetooth state, and show blocking preflight reasons. Settings and runtime readiness are not durable yet.

For the current one-reader operation, `ReaderSettings` pre-populates the RP902 Bluetooth MAC address with `DC:0D:30:DA:0F:3C`. The gateway mode still defaults to fake; the default MAC does not cause real RP902 connection attempts until `Real RP902` is explicitly selected.

## Minimal Real-Device Smoke-Test Path

1. Keep the app in fake mode for normal local operation.
2. Open Settings and explicitly select `Real RP902`.
3. Confirm the default RP902 Bluetooth MAC address `DC:0D:30:DA:0F:3C`, or edit it if a different paired RP902 is being used.
4. Tap `Request permissions` if permissions are missing.
5. Ensure Bluetooth is enabled on the Android device, then tap `Refresh`.
6. Confirm the preflight status says the real RP902 connection can be attempted.
7. Open Inventory and tap `Connect`.
8. Tap `Start` and watch structured reader logs for listener attachment, `inventory6c()` result, and tag callbacks.
9. Tap `Stop`, confirm the stop summary, then tap `Disconnect`.

## DisplayOutput Crash Workaround

Hardware testing showed a crash immediately after inventory start, before tag callbacks, through the vendor `DisplayOutput` JNI path. The current workaround affects only the real RP902 adapter:

- Instantiate `DisplayOutputSuppressingRp902Reader` instead of plain `RP902Reader`.
- Override only `setDisplayOutput(DisplayOutput)` and log suppression instead of entering vendor JNI.
- Configure minimal continuous inventory display behavior before `inventory6c()`.

This should be removed or revisited if Unitech provides an SDK where the vendor `DisplayOutput` path is safe on the target Android runtime.

## Items To Confirm Later

- Runtime permission UX behavior on target hardware and MDM-managed devices.
- Bluetooth MAC address acquisition, validation, and pairing flow for future additional readers.
- Whether RP902 requires DMService or key mapping service for the intended device fleet.
- Threading requirements for vendor callbacks.
- Whether Unitech has a fixed RP902 SDK/FW for the `DisplayOutput.parameter` CheckJNI abort.
- PostgreSQL View / Function names and row contracts for work context, rule bundle, equipment snapshot, read result registration, and final result view.
- SSL mode, server certificate provisioning, credential storage, and network policy for managed Android devices.
- Durable pending write storage requirements.
- Structured log retention and export requirements.

## Notes

- Do not guess vendor SDK behavior beyond confirmed Javadoc and sample calls.
- Keep vendor classes isolated inside the real reader adapter.
- Keep the fake implementation usable for local development and tests.
- Do not use the sample keystore or sample signing configuration.
