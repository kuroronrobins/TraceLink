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

## Current Stage

The actual vendor SDK API is not introduced yet. The first implementation uses `ReaderGateway` and `FakeReaderGateway` so UI, session logic, upload payloads, and tests can move forward without inventing vendor APIs.

## Items To Confirm Later

- Supported Android SDK requirements.
- RP902 SDK package and initialization requirements.
- Connection method.
- Inventory start and stop API.
- Permission requirements.
- Android version and Bluetooth dependencies.

## Notes

- Do not guess vendor SDK API names.
- Add the real implementation only after vendor files are available.
- Keep the fake implementation usable for local development and tests.

