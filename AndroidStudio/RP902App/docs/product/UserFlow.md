# User Flow

## Basic Flow

1. Launch the app.
2. Check RP902 connection state.
3. Connect the reader.
4. Confirm work context.
5. Fetch rule bundle and equipment snapshot.
6. Start inventory.
7. Confirm EPC values in the live list.
8. Stop inventory when needed.
9. Run local judgement.
10. Register the read result bundle to PostgreSQL.
11. Confirm success or failure.
12. XCgate reads final results from PostgreSQL.

## Connection States

- Disconnected
- Connecting
- Connected
- Error

## Inventory

- Single-session inventory.
- Continuous reads from the reader gateway.
- Duplicate EPC values are merged in the current session.

## Result Registration

- Registration happens per session.
- Success is shown as completed.
- Failure remains retryable as pending write.

## Logs

- Connection events.
- Inventory events.
- Result registration events.
- Errors.
