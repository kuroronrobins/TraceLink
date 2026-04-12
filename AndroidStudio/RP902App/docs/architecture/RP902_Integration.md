# RP902 Integration

## Policy

RP902 vendor SDK code must stay outside composables and ViewModels. UI and ViewModels depend on app-owned contracts, not directly on vendor classes.

## Target Structure

- UI layer
- ViewModel
- Repository/use-case layer
- ReaderGateway interface
- RP902 real implementation
- RP902 fake implementation

## First Slice Package Structure

The first implementation slice remains in the single `:app` module and separates packages by layer:

- `domain/reader`: app-owned reader gateway contract and reader state models.
- `domain/inventory`: pure session and duplicate-filtering logic plus inventory repository contract.
- `domain/upload`: upload payload, upload transport contract, and upload state models.
- `domain/log`: structured app log models and log store contract.
- `data/reader`: fake reader gateway for local development.
- `data/inventory`: default inventory repository orchestration.
- `data/upload`: fake upload transport and in-memory upload retry queue.
- `data/log`: in-memory event log store.
- `ui/app`: app shell and route selection.
- `ui/inventory`: inventory screen state projection and Compose UI.
- `ui/logs`: event log screen.

The ViewModel exposes UI state as a projection of repository state. Composables receive state and callbacks only; they do not call reader or upload transports directly.

`DefaultInventoryRepository` orchestrates reader events, session duplicate filtering, upload attempts, retry queue updates, and structured log writes. Reader access, upload transport, retry queue storage, and log storage remain separate contracts so the real RP902 implementation and durable storage can be swapped in later without changing composables.

## Current Stage

The app still defaults to `FakeReaderGateway`. The Unitech AAR/JAR are now added to the app as local file dependencies so `RealRp902Gateway` can compile against confirmed vendor types. The real gateway remains behind the app-owned `ReaderGateway` contract and is only selected when reader settings are changed from fake to real.

`AppContainer` is the switch point:

- `ReaderGatewayMode.Fake`: default, local development path.
- `ReaderGatewayMode.RealRp902`: explicit opt-in path for hardware preparation.

`ConfigurableReaderGateway` listens to app-owned reader settings and swaps the active gateway between fake and real implementations. Switching modes disconnects the previous gateway first. This keeps composables and ViewModels free from vendor SDK calls.

The current real gateway uses only confirmed vendor API entry points:

- `TransportBluetooth(DeviceType.RP902, "RP902", bluetoothMacAddress)`.
- `RP902Reader(transport)`.
- `reader.addListener(IReaderEventListener)`.
- `reader.connect()`.
- `reader.disconnect()`.
- `reader.clearListener()`.
- `reader.getRfidUhf().addListener(IRfidUhfEventListener)`.
- `reader.getRfidUhf().inventory6c()`.
- `reader.getRfidUhf().stop()`.
- `reader.getRfidUhf().removeListener(...)`.

The real path still requires runtime permission handling, Bluetooth enablement checks, final inventory tuning, and hardware validation before it should be used in production.

## Vendor Dependency Wiring

The app module references the checked-in vendor files directly:

- `vendor/unitech/Unitech_RFID_SDK_Android_V1_0_41/1.0.41/Binary/unitechRFID_v1.0.41.aar`.
- `vendor/unitech/Unitech_RFID_SDK_Android_V1_0_41/1.0.41/Source/AndroidStudio/unitechRFIDSample/app/libs/UnitechSDK_1.2.19.jar`.

No sample signing config or sample keystore is used. The dependency is intentionally local and explicit because the project uses `RepositoriesMode.FAIL_ON_PROJECT_REPOS` and there is no internal artifact repository configured yet.

Current lint output reports that vendor native libraries such as `libJNISTUHFL.so`, `libSTUHFL.so`, and `librfidapi.so` are not 16 KB aligned. This is a vendor SDK compatibility risk for devices that require 16 KB page-size alignment and must be resolved with an updated vendor SDK or a vendor-supported packaging plan before release.

## Reader Settings And Preflight

The app now has app-owned reader settings:

- `ReaderSettings`: selected gateway mode and optional normalized Bluetooth MAC address.
- `ReaderBluetoothAddress`: pure parser/normalizer for `00:11:22:33:44:55` and `001122334455` formats.
- `ReaderConnectionPreflight`: pure helper for required runtime permissions and connection readiness checks.
- `AndroidReaderPermissionMapper`: Android permission string mapping kept outside the domain layer.

The settings screen is a simple in-memory foundation. It can switch between fake and real gateway modes and edit the RP902 Bluetooth MAC address. Settings are not durable yet.

## Confirmed Vendor SDK Surface

The following items were confirmed from Unitech SDK 1.0.41 Javadoc and the bundled `unitechRFIDSample` source. This section records observed API names only; it is not an implementation plan for unchecked behavior.

### Reader And Transport

- `com.unitech.lib.rpx.RP902Reader`
  - Extends `com.unitech.lib.reader.BaseReader`.
  - Constructor: `RP902Reader(BaseTransport)`.
  - Observed sample creation path:
    - `TransportBluetooth(DeviceType.RP902, "RP902", bluetoothMacAddress)`.
    - `RP902Reader(transport)`.
    - `reader.addListener(IReaderEventListener)`.
    - `reader.connect()`.
- `com.unitech.lib.reader.BaseReader`
  - Confirmed methods include `connect()`, `disconnect()`, `addListener(...)`, `removeListener(...)`, `clearListener()`, `getState()`, `getAction()`, and `getRfidUhf()`.
- `com.unitech.lib.transport.TransportBluetooth`
  - Confirmed constructor includes `TransportBluetooth(DeviceType, String, String)`.
  - Confirmed methods include `connect()`, `disconnect()`, `listen()`, and `setStateListener(...)`.
- `com.unitech.lib.transport.types.ConnectState`
  - Confirmed states include `Disconnected`, `Connecting`, `Connected`, and `Listen`.

### Inventory

- `com.unitech.lib.uhf.BaseUHF`
  - Confirmed methods include `addListener(IRfidUhfEventListener)`, `removeListener(...)`, `inventory6c()`, and `stop()`.
- `com.unitech.lib.types.ActionState`
  - Sample branches on `ActionState.Stop` and `ActionState.Inventory6c`.
- Sample inventory start path:
  - Apply reader/UHF settings.
  - `reader.setDisplayTags(...)`.
  - `reader.getRfidUhf().inventory6c()`.
- Sample inventory stop path:
  - `reader.getRfidUhf().stop()`.

### Events

- `com.unitech.lib.reader.event.IReaderEventListener`
  - Confirmed callbacks include:
    - `onReaderStateChanged(BaseReader, ConnectState, Object)`.
    - `onReaderActionChanged(BaseReader, ResultCode, ActionState, Object)`.
    - `onReaderBatteryState(BaseReader, int, Object)`.
    - `onReaderKeyChanged(BaseReader, KeyType, KeyState, Object)`.
    - `onNotificationState(NotificationState, Object)`.
    - `onReaderTemperatureState(BaseReader, double, Object)`.
- `com.unitech.lib.uhf.event.IRfidUhfEventListener`
  - Confirmed tag callback: `onRfidUhfReadTag(BaseUHF, String, Object)`.
  - Sample treats the `String` argument as the EPC and optionally casts `params` to `TagExtParam` to read RSSI/TID.

### Shutdown Behavior Observed In Sample

- On pause/stop paths, the sample stops UHF inventory before disconnecting:
  - If `reader.getAction() != ActionState.Stop`, call `reader.getRfidUhf().stop()`.
  - Then call `reader.disconnect()`.

## Permissions And Runtime Dependencies

The bundled sample manifest declares:

- `android.permission.ACCESS_FINE_LOCATION`.
- `android.permission.ACCESS_COARSE_LOCATION`.
- `android.permission.BLUETOOTH`.
- `android.permission.BLUETOOTH_ADMIN`.
- `android.permission.INJECT_EVENTS`.

The current app manifest declares the preparation permissions while the default path remains fake:

- `BLUETOOTH` and `BLUETOOTH_ADMIN` with `maxSdkVersion="30"`.
- `BLUETOOTH_CONNECT`.
- `BLUETOOTH_SCAN`.
- `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` with `maxSdkVersion="30"`.

Before enabling the real gateway for production, confirm the Android 12+ Bluetooth permission set for the target device and decide whether location permission remains required by the SDK or scan path.

The sample also uses Bluetooth enablement checks via `BluetoothAdapter` and contains key mapping / scan service calls such as `KeymappingCtrl` and `unitech.scanservice.software_scankey`. Vendor binaries include DMService APKs, but the sample manifest and Javadocs reviewed here do not prove whether DMService is required for the RP902 inventory path. Treat DMService and key mapping as device-environment dependencies to confirm on hardware.

## Items To Confirm Later

- Runtime permission UX for target SDK 35.
- Bluetooth MAC address acquisition, validation, and pairing flow.
- Whether RP902 requires DMService or key mapping service for the intended device fleet.
- Final mapping from vendor `ConnectState` and `ActionState` to app-owned `ReaderConnectionState` and inventory-running state.
- Threading requirements for vendor callbacks.
- Whether local vendor file dependencies should be replaced with an internal artifact repository before CI/release hardening.
- Vendor SDK native library 16 KB page-size alignment support.
- Durable retry queue storage requirements.
- Structured log retention and export requirements.

## Notes

- Do not guess vendor SDK behavior beyond confirmed Javadoc and sample calls.
- Keep vendor classes isolated inside the real reader adapter.
- Keep the fake implementation usable for local development and tests.
- Do not use the sample keystore or sample signing configuration.
