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
- `workId`
- `reportId`
- `operatorId`
- `ruleVersion`
- `equipmentSnapshotVersion`
- `tags`

tag fields:

- `epc`
- `firstSeenAtEpochMillis`
- `lastSeenAtEpochMillis`
- `readCount`
- `judgementStatus`
- `judgementReasonCode`

`JdbcPostgresGateway` はこの bundle を JSONB 文字列へ変換し、registration function の単一引数として渡す。
最終的な JSON schema と function 戻り値は PostgreSQL 側契約と合わせて固定する。

## Pending Write

PostgreSQL function 呼び出しに失敗した non-empty bundle は、retryable failure の場合だけ `PendingWriteQueue` に残す。
configuration failure や contract/schema failure は、同じ bundle を再送しても成功しない可能性が高いため queue へ積まない。
現在は in-memory 実装だが、本番前に durable storage へ差し替える。

pending write の重複キーは現時点では `sessionId` とする。最終的な冪等性キーは PostgreSQL function 契約と合わせて確定する。

## PostgreSQL の責務

- rule/master/result の整合性を制約で守る。
- Android 用 View / Function を通して必要情報だけ公開する。
- result registration function で冪等性と入力検証を行う。
- audit 情報を保持する。
- XCgate 用 final result view を提供する。

## Current Status

`JdbcPostgresGateway` による PostgreSQL access layer を追加済み。
アプリの default は fake mode のままで、`AppContainer` に `DataAccessMode.Postgres` と `PostgresConnectionSettings` を渡した場合だけ PostgreSQL mode を組み立てる。

実 DB への live 接続テストはこの段階では行っていない。
View / Function 名、row column、registration JSON schema は `data.postgres` 配下に集約しているが、DB 側契約の最終確定が必要。
