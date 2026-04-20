# PostgreSQL Access Contract

## Purpose

この文書は、Android アプリが PostgreSQL を安全に利用するための境界を定義する。専用中間アプリケーションは置かない。

## Access Policy

- Android は PostgreSQL に直接アクセスする。
- Android は table 直叩きを前提にしない。
- PostgreSQL は Android 用 View / Function / 制約を公開する。
- 読取結果登録は function 経由で行う。
- XCgate は final result view を参照する。

## Android が取得する情報

| 目的 | PostgreSQL 側の境界 | Android 側の受け口 |
| --- | --- | --- |
| 作業対象確定 | work context view / function | `WorkContextRepository` |
| 帳票ルール取得 | rule bundle view / function | `RuleRepository` |
| 設備マスタ取得 | equipment snapshot view / function | `EquipmentMasterRepository` |
| 読取結果登録 | read result registration function | `ReadResultRepository` |
| 登録結果確認 | registration result view | `ReadResultRepository` |

## Read Result Registration Bundle

登録単位は session ごとの bundle とする。現時点の Android 側モデルは以下。

- `sessionId`
- `registeredAtEpochMillis`
- `deviceId`
- `readerType`
- `tags`

tag fields:

- `epc`
- `firstSeenAtEpochMillis`
- `lastSeenAtEpochMillis`
- `readCount`

端末内判定の本実装後は、判定結果、理由コード、rule/master snapshot version を bundle に追加する。

## Pending Write

PostgreSQL function 呼び出しに失敗した non-empty bundle は `PendingWriteQueue` に残す。現在は in-memory 実装だが、本番前に durable storage へ差し替える。

pending write の重複キーは現時点では `sessionId` とする。最終的な冪等性キーは PostgreSQL function 契約と合わせて確定する。

## PostgreSQL の責務

- rule/master/result の整合性を制約で守る。
- Android 用 View / Function を通して必要情報だけ公開する。
- result registration function で冪等性と入力検証を行う。
- audit 情報を保持する。
- XCgate 用 final result view を提供する。

## Current Status

実 PostgreSQL 接続は未実装。現在は `ReadResultRepository` と `FakeReadResultRepository` で差し替え点を確保している。
