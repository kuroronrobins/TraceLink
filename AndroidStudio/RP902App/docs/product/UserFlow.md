# User Flow

## Basic Flow

1. Launch the app.
2. Check RP902 connection state.
3. Connect the reader.
4. Start inventory.
5. Confirm EPC values in the live list.
6. Stop inventory when needed.
7. Upload the session result.
8. Confirm success or failure.

## Connection States

- Disconnected
- Connecting
- Connected
- Error

## Inventory

- Single-session inventory.
- Continuous reads from the reader gateway.
- Duplicate EPC values are merged in the current session.

## Upload

- Upload happens per session.
- Success is shown as completed.
- Failure remains retryable.

## Logs

- Connection events.
- Inventory events.
- Upload events.
- Errors.

