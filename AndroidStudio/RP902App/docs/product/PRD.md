# PRD

## Product Name

TraceLink RP902 App

## Background

Android 端末と RP902 UHF reader を使い、現場で RFID を読み取る。Android アプリは作業対象、帳票ルール、設備マスタを PostgreSQL から取得し、端末内で判定し、読取結果を PostgreSQL に登録する。

## Goals

- RP902 から RFID タグを読み取る。
- 1 つの session 内で EPC を重複除去する。
- PostgreSQL から rule/master/context を取得する差し替え点を持つ。
- Android 端末内で判定する。
- 判定済み読取結果を PostgreSQL function 経由で登録する。
- 登録状態、pending write、失敗理由を現場で見えるようにする。

## In Scope

- RP902 connection state.
- Start and stop inventory.
- EPC list display.
- Duplicate filtering per session.
- Read result registration contract.
- Pending write handling.
- Event log display.

## Out Of Scope

- PostgreSQL 実接続の完成実装。
- 帳票ルール本実装。
- 設備マスタ本実装。
- 高度な inventory tuning。

## Success Criteria

- The app can connect to an RP902-compatible gateway.
- Tags can be read.
- Duplicate EPC values are not displayed as separate session rows.
- A read result registration bundle can be prepared.
- Registration failure remains visible and retryable as pending write.
- Field operators can understand current state.
