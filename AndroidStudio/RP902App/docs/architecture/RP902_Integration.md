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

`ConfigurableReaderGateway` listens to app-owned reader settings and swaps the active gateway between fake and real implementations. Switching modes disconnects the previous gateway first. For the real RP902 path, it also checks app-owned runtime preflight state before delegating to the vendor-backed gateway. This keeps composables and ViewModels free from vendor SDK calls.

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

The real path now has enough app-owned preparation to attempt a minimal hardware smoke test when explicitly selected:

- Settings can request the Android runtime permissions required for the selected SDK level.
- Settings refreshes and displays Bluetooth adapter state.
- Missing MAC address, missing permissions, Bluetooth disabled/unavailable/unknown, and Bluetooth status permission failures are represented as preflight blockers.
- Inventory connect attempts in real mode are blocked at the adapter boundary when preflight fails, before vendor SDK calls are made.
- Gateway diagnostic events are written into the structured reader log.
- `RealRp902Gateway` logs connect/disconnect/inventory start/stop requests, vendor method return values, and whether vendor callbacks are reached.
- RP902 vendor callbacks are guarded so null callback arguments, blank/oversized EPC values, and app model conversion failures are logged instead of escaping the callback thread.
- The UHF listener is attached once per active `BaseUHF` instance and reused for inventory start to avoid duplicate listener registration during tag dispatch. The adapter now records listener identity and reattaches if `getRfidUhf()` returns a different instance between connect and start.
- `RealRp902Gateway` uses a small `RP902Reader` subclass that suppresses `setDisplayOutput(DisplayOutput)` before it reaches vendor JNI. This avoids the observed Android CheckJNI abort in `BR_DisplayOutput._Set_DisplayOutput` while keeping connect and `inventory6c()` on confirmed vendor APIs.
- Real gateway connect/disconnect/start/stop commands run on a command dispatcher with a mutex so vendor calls do not block the Compose main thread or overlap each other.
- Inventory start now applies the sample-backed UHF inventory settings confirmed from `SampleFragment.initSetting()` before calling `inventory6c()`: session S0, continuous mode, inventory/idle timing, DynamicQ, Q values, target/toggle target, RP902 power 22, TARI 25.00, BLF 256, and fast mode. Nonessential sample settings such as beeper, vibrator, screen-off, auto-off, and time setting remain out of the app path for now.
- Inventory start also clears the two sample select mask slots before inventory so a persisted select mask from prior testing is less likely to suppress reads.

The real path still requires real-device validation, final inventory tuning, pairing/MAC acquisition decisions, and callback threading validation before production use.

## Vendor Dependency Wiring

The app module references stable runtime files only:

- `vendor/unitech/runtime/unitechRFID_v1.0.41.aar`.
- `vendor/unitech/runtime/UnitechSDK_1.2.19.jar`.

The original SDK distribution is preserved separately under `vendor/unitech/upstream/Unitech_RFID_SDK_Android_V1_0_41/`. Javadoc and the programming guide are copied to `vendor/unitech/docs/` for shorter reference paths.

No sample source, sample APK, sample signing config, sample keystore, Xamarin project, or upstream Gradle wrapper is used by the app build. The dependency is intentionally local and explicit because the project uses `RepositoriesMode.FAIL_ON_PROJECT_REPOS` and there is no internal artifact repository configured yet.

Current lint output reports that `arm64-v8a/libJNISTUHFL.so` from `vendor/unitech/runtime/unitechRFID_v1.0.41.aar` is not 16 KB aligned. This is a vendor SDK compatibility risk for devices that require 16 KB page-size alignment and must be resolved with an updated vendor SDK or a vendor-supported packaging plan before release. Details are tracked in `docs/maintenance/16kb_alignment_assessment.md`.

## Reader Settings And Preflight

The app now has app-owned reader settings:

- `ReaderSettings`: selected gateway mode and optional normalized Bluetooth MAC address.
- `ReaderBluetoothAddress`: pure parser/normalizer for `00:11:22:33:44:55` and `001122334455` formats.
- `ReaderConnectionPreflight`: pure helper for required runtime permissions and connection readiness checks.
- `ReaderRuntimeState`: app-owned runtime snapshot of granted reader permissions and Bluetooth adapter state.
- `AndroidReaderPermissionMapper`: Android permission string mapping kept outside the domain layer.

The settings screen is a simple in-memory foundation. It can switch between fake and real gateway modes, edit the RP902 Bluetooth MAC address, request runtime permissions, refresh Bluetooth state, and show blocking preflight reasons. Settings and runtime readiness are not durable yet.

For the current one-reader operation, `ReaderSettings` pre-populates the RP902 Bluetooth MAC address with `DC:0D:30:DA:0F:3C`. This reduces setup friction while keeping the Settings field editable for future replacement or multi-reader scenarios. The gateway mode still defaults to fake; the default MAC does not cause real RP902 connection attempts until `Real RP902` is explicitly selected.

For Android 12+ (`sdkInt >= 31`), the app-owned preflight currently requires:

- `BLUETOOTH_CONNECT`.
- `BLUETOOTH_SCAN`.

For pre-Android 12 targets, the app-owned preflight currently requires:

- `BLUETOOTH`.
- `BLUETOOTH_ADMIN`.
- `ACCESS_FINE_LOCATION`.
- `ACCESS_COARSE_LOCATION`.

This matches the app preparation model and remains subject to hardware verification. The final target fleet may require narrower or broader permission handling depending on RP902 pairing, scan, and vendor SDK behavior.

## Minimal Real-Device Smoke-Test Path

The intended first hardware test flow is:

1. Keep the app in fake mode for normal local operation.
2. Open Settings and explicitly select `Real RP902`.
3. Confirm the default RP902 Bluetooth MAC address `DC:0D:30:DA:0F:3C`, or edit it if a different paired RP902 is being used.
4. Tap `Request permissions` if permissions are missing.
5. Ensure Bluetooth is enabled on the Android device, then tap `Refresh`.
6. Confirm the preflight status says the real RP902 connection can be attempted.
7. Open Inventory and tap `Connect`.
8. Watch Logs for:
   - `Gateway mode: RealRp902`.
   - `Preflight result: ready` or `Preflight result: blocked`.
   - `RP902 connect started`.
   - `RP902 connect() invoked; waiting for vendor state callback`.
   - `Vendor callback onReaderStateChanged reached`.
9. If connected, tap `Start` and watch for:
   - `RP902 inventory start requested`.
   - `RP902 inventory listener preflight`.
   - `RP902 UHF listener attached` or `RP902 UHF listener already attached`.
   - `RP902 inventory listener ready`.
   - `RP902 sample-backed UHF tuning completed`.
   - `RP902 select mask clear completed`.
   - `RP902 DisplayTags configured for minimal inventory: readOnce=Off beepVibrate=Off`.
   - `Calling inventory6c(); RP902 DisplayOutput JNI path is suppressed`.
   - `RP902 DisplayOutput suppressed before vendor JNI`.
   - `RP902 inventory6c() returned ...`.
   - `First RP902 tag callback reached`.
   - `Vendor tag callback #...`.
   - `RP902 tag model conversion succeeded`.
   - `Read EPC ...`.
10. Tap `Stop` and watch for the `RP902 stop() returned ... callbacks=... acceptedTags=... lastTagAt=...` summary, then tap `Disconnect`.

If a vendor callback log is absent, the current slice has not proven that the SDK delivered the event. Check pairing, MAC address, Android Bluetooth permission state, and whether the RP902/device environment requires additional vendor services.

## Inventory Responsiveness And Callback Diagnostics

The April 2026 hardware log showed `STUHFL_F_StartOOP` and `STUHFL_F_Stop` running on the app main thread, followed by skipped frames. The adapter now dispatches reader commands away from the UI thread. If skipped frames remain after this change, the remaining cause is likely callback volume, Compose rendering, or vendor work performed internally on the main thread.

The real gateway records the following inventory diagnostics in structured logs:

- start request thread, connected state, listener attachment state, and inventory attempt number.
- listener object hash, attached UHF object hash, active inventory UHF object hash, attach/detach counts, callback count, and last callback timestamp.
- sample-backed UHF tuning success/failure count.
- select mask clear success/failure count.
- `inventory6c()` return result and elapsed command time.
- first tag callback arrival, callback thread, raw tag presence/length, params type, `TagExtParam` presence, RSSI presence, and TID presence.
- first few tag callback diagnostics and then every 100th callback.
- stop summary with total callbacks, accepted tags, mapping failures, running duration, and last tag callback timestamp.

If `callbacks=0` appears in the stop summary after a real tag was presented, the app did not observe `IRfidUhfEventListener.onRfidUhfReadTag(...)` for that inventory run. That points below the repository/UI path: listener attachment order, RP902 inventory settings, tag/environment conditions, pairing, firmware, or vendor services. If callbacks are greater than zero but `acceptedTags=0`, inspect the raw payload and mapping failure logs. If accepted tags are greater than zero but no `Read EPC ... repositoryAccepted=true` log appears, inspect the gateway-to-repository flow.

The app intentionally does not enable the sample's hardware trigger/key-mapping path for the on-screen Start button flow. The sample calls `setUseGunKeyCode()` after connection so the physical trigger can start and stop inventory, using `KeymappingCtrl` and the `unitech.scanservice.software_scankey` broadcast. This remains a hardware-operation dependency to validate if physical trigger operation is required, but it is not expected to be required for the app's explicit Start button calling `inventory6c()`.

## DisplayOutput Crash Workaround

Hardware testing showed a crash immediately after inventory start, before tag callbacks:

- `BR_DisplayOutput._Set_DisplayOutput`.
- `STUHFL_T_DisplayOutput.commit`.
- `DeviceRP902.setDisplayOutput`.
- `RP902Reader.setDisplayOutput`.
- `RP902Reader$KeepScanThread.run`.

The abort message was `attempt to access field byte com.unitech.lib.reader.params.DisplayOutput.parameter of type byte with the wrong type int`. The app did not call `setDisplayOutput` directly. AAR bytecode inspection showed that `RP902Reader$KeepScanThread` calls `setDisplayOutput(new DisplayOutput(...))` while inventory is running, including the initial `Scan` display path when no tags have been seen yet.

The current workaround keeps the default fake path unchanged and affects only the real RP902 adapter:

- Instantiate `DisplayOutputSuppressingRp902Reader` instead of plain `RP902Reader`.
- Override only `setDisplayOutput(DisplayOutput)` and log suppression instead of entering vendor JNI.
- Configure `DisplayTags(ReadOnceState.Off, BeepAndVibrateState.Off)` before `inventory6c()` to keep the minimal continuous inventory path and avoid nonessential display/beep/vibrate output.

This is a defensive adapter-boundary workaround for a vendor SDK/FW issue. It should be removed or revisited if Unitech provides an SDK where `BR_DisplayOutput._Set_DisplayOutput` is safe on the target Android runtime.

## Confirmed Vendor SDK Surface

The following items were confirmed from Unitech SDK 1.0.41 Javadoc and the bundled `unitechRFIDSample` source preserved in `vendor/unitech/upstream/`. This section records observed API names only; it is not an implementation plan for unchecked behavior.

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

Before enabling the real gateway for production, confirm the Android 12+ Bluetooth permission set for the target device and decide whether location permission remains required by the SDK or scan path. Also confirm whether `BLUETOOTH_SCAN` needs `usesPermissionFlags="neverForLocation"` for the intended behavior; this has not been decided.

The sample also uses Bluetooth enablement checks via `BluetoothAdapter` and contains key mapping / scan service calls such as `KeymappingCtrl` and `unitech.scanservice.software_scankey`. Vendor binaries include DMService APKs, but the sample manifest and Javadocs reviewed here do not prove whether DMService is required for the RP902 inventory path. Treat DMService and key mapping as device-environment dependencies to confirm on hardware.

## Items To Confirm Later

- Runtime permission UX behavior on target hardware and MDM-managed devices.
- Bluetooth MAC address acquisition, validation, and pairing flow for any future additional readers. The current one-reader default is `DC:0D:30:DA:0F:3C` and remains editable in Settings.
- Whether RP902 requires DMService or key mapping service for the intended device fleet.
- Final mapping from vendor `ConnectState` and `ActionState` to app-owned `ReaderConnectionState` and inventory-running state.
- Threading requirements for vendor callbacks.
- Whether Unitech has a fixed RP902 SDK/FW for the `DisplayOutput.parameter` CheckJNI abort.
- Whether local vendor file dependencies should be replaced with an internal artifact repository before CI/release hardening.
- License and redistribution requirements for keeping vendor binaries/docs in the repository.
- Vendor SDK native library 16 KB page-size alignment support.
- Durable retry queue storage requirements.
- Structured log retention and export requirements.
- Release signing configuration and sample-keystore exclusion are tracked in `docs/maintenance/signing_and_keystore_audit.md`.
- Current in-memory stores and persistence priorities are tracked in `docs/maintenance/in_memory_inventory.md`.

## Notes

- Do not guess vendor SDK behavior beyond confirmed Javadoc and sample calls.
- Keep vendor classes isolated inside the real reader adapter.
- Keep the fake implementation usable for local development and tests.
- Do not use the sample keystore or sample signing configuration.
