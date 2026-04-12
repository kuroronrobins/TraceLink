# PRD

## Product Name

TraceLink RP902 App

## Background

The app turns an Android device connected to an RP902 UHF reader into a field inventory terminal. The Android side is responsible for reading tags, showing session state, and sending read results. Final business judgement remains on the backend.

## Goals

- Read tags from RP902.
- Remove duplicate EPC values inside one inventory session.
- Attach required metadata for backend upload.
- Make connection, upload, and failure states visible in the field.

## In Scope

- RP902 connection state.
- Start and stop inventory.
- EPC list display.
- Duplicate filtering per session.
- Backend upload contract.
- Retryable upload failure handling.
- Event log display.

## Out Of Scope

- Account/business-rule judgement.
- Final equipment/master-data matching.
- Database persistence implementation.
- Advanced inventory optimization.

## Success Criteria

- The app can connect to an RP902-compatible gateway.
- Tags can be read.
- Duplicate EPC values are not displayed as separate session rows.
- The session payload can be prepared for backend upload.
- Failed uploads can be retried.
- Field operators can understand current state.

