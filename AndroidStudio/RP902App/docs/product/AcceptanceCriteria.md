# Acceptance Criteria

## Connection

- After app launch, RP902 connection state is visible.
- Connect action updates the connection state.
- Disconnect action updates the connection state.

## Inventory

- Starting inventory updates the live tag list.
- The same EPC is not added as a second row in the same session.
- Duplicate reads increment read count.
- Stop inventory works.

## Screen

- Connection state is visible.
- Total unique tag count is visible.
- EPC list is visible.
- Upload state is visible.

## Upload

- Upload action prepares and submits a session payload.
- Success is shown clearly.
- Failure remains visible and retryable.

## Logs

- Connection events are recorded.
- Inventory events are recorded.
- Upload events are recorded.
- Errors are understandable.

