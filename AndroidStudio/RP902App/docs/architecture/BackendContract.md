# Backend Contract

## Purpose

The Android app owns reading and upload orchestration. Final judgement is performed by the backend.

## Upload Unit

Upload is performed per inventory session.

Failed non-empty session uploads are stored in a local retry queue until durable storage and the final backend behavior are confirmed.

Retry queue entries use `sessionId` as the local de-duplication key.

## Required Payload Fields

- `sessionId`
- `sentAtEpochMillis`
- `deviceId`
- `readerType`
- `tags`

## Tag Fields

- `epc`
- `firstSeenAtEpochMillis`
- `lastSeenAtEpochMillis`
- `readCount`

## Current Status

The final API endpoint and request schema are not confirmed. Treat the current model as a local contract stub until backend details are fixed.
