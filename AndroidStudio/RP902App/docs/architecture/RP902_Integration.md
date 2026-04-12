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

The app still defaults to `FakeReaderGateway`. `RealRp902Gateway` exists only as an adapter boundary and does not import or call the vendor SDK yet. It implements `ReaderGateway` and reports a not-enabled reader error until SDK dependencies, runtime permissions, Bluetooth address handling, and callback mapping are configured.

`AppContainer` is the switch point:

- `ReaderGatewayMode.Fake`: default, local development path.
- `ReaderGatewayMode.RealRp902`: explicit opt-in placeholder for future real-device wiring.

No vendor AAR/JAR dependency has been added to the app module in this step.

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

The current app manifest is intentionally not changed yet because the default path is still fake and target SDK is 35. Before enabling the real gateway, confirm the Android 12+ Bluetooth permission set for the target device, likely including `BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN`, and decide whether location permission remains required by the SDK or scan path.

The sample also uses Bluetooth enablement checks via `BluetoothAdapter` and contains key mapping / scan service calls such as `KeymappingCtrl` and `unitech.scanservice.software_scankey`. Vendor binaries include DMService APKs, but the sample manifest and Javadocs reviewed here do not prove whether DMService is required for the RP902 inventory path. Treat DMService and key mapping as device-environment dependencies to confirm on hardware.

## Items To Confirm Later

- Exact app dependency wiring for `unitechRFID_v1.0.41.aar` and `UnitechSDK_1.2.19.jar`.
- Whether the app should vendor-copy binaries, reference checked-in local files, or use an internal artifact repository.
- Runtime permission UX for target SDK 35.
- Bluetooth MAC address acquisition, validation, and pairing flow.
- Whether RP902 requires DMService or key mapping service for the intended device fleet.
- Final mapping from vendor `ConnectState` and `ActionState` to app-owned `ReaderConnectionState` and inventory-running state.
- Threading requirements for vendor callbacks.
- Durable retry queue storage requirements.
- Structured log retention and export requirements.

## Notes

- Do not guess vendor SDK behavior beyond confirmed Javadoc and sample calls.
- Keep vendor classes isolated inside the real reader adapter.
- Keep the fake implementation usable for local development and tests.
- Do not use the sample keystore or sample signing configuration.
