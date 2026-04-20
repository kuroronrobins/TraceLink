# PostgreSQL Access Contract

この文書は Android アプリと PostgreSQL の正式な View / Function 契約を定義する。
DB 実装者はこの契約に合わせて `api` schema の object を実装する。
Android 実装は `data.postgres` 配下だけが SQL、object 名、row 形状、JSONB 形状を知ってよい。

## 原則

- Android は PostgreSQL に直接アクセスする。
- Android は table を直接参照しない。
- PostgreSQL は Android 用の Function と制約を公開する。
- SQL、schema 名、Function 名、column 名は `data.postgres` に集約する。
- UI、ViewModel、`DefaultInventoryRepository`、`InventorySession`、`ReadJudgementService` に SQL や table 名を出さない。
- 時刻は Unix epoch milliseconds を `bigint` で扱う。
- EPC は Android 側で trim + uppercase 済みの値を送る。DB 側も登録時に再検証する。

## View / Function Inventory

| object name | type | purpose | input | output | cardinality | failure semantics | Kotlin caller |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `api.fn_get_active_work_context` | Function | 端末に割り当てられた現在の作業対象を返す | `p_device_id text` | work context row | exactly 1 row | 0 rows / multiple rows は contract mismatch。対象未割当を DB 側で明示したい場合は、0 rows ではなく SQL exception として扱う | `JdbcPostgresGateway.fetchWorkContext` |
| `api.fn_get_rule_bundle` | Function | 作業対象に紐づく帳票ルール bundle を返す | `p_work_id text` | rule bundle row | exactly 1 row | 0 rows / multiple rows は contract mismatch | `JdbcPostgresGateway.fetchRuleBundle` |
| `api.fn_get_equipment_snapshot` | Function | 作業対象に必要な設備 master snapshot を返す | `p_work_id text` | equipment snapshot rows | 1 or more rows | 0 rows は contract mismatch。空 snapshot は許可しない | `JdbcPostgresGateway.fetchEquipmentSnapshot` |
| `api.fn_register_read_result_bundle` | Function | 判定済み読取結果 bundle を登録する | `p_bundle jsonb` | registration result row | exactly 1 row | 入力検証失敗は result row で返す。通信断や DB 内部障害は SQL exception として扱う | `JdbcPostgresGateway.registerReadResults` |

## Work Context Contract

```sql
api.fn_get_active_work_context(p_device_id text)
returns table (
    work_id text,
    report_id text,
    operator_id text,
    started_at_epoch_millis bigint
)
```

Android は作業対象を `deviceId` で特定する。
`work_id` は以降の rule / equipment 取得の key になる。

| column | PostgreSQL type | nullable | meaning | Kotlin field | notes |
| --- | --- | --- | --- | --- | --- |
| `work_id` | `text` | not null | 作業単位 ID | `WorkContext.workId` | `fn_get_rule_bundle` / `fn_get_equipment_snapshot` の入力 |
| `report_id` | `text` | not null | XCgate 帳票または帳票種別の ID | `WorkContext.reportId` | `RuleBundle.reportId` と一致すること |
| `operator_id` | `text` | nullable | 作業者 ID | `WorkContext.operatorId` | 不明なら null |
| `started_at_epoch_millis` | `bigint` | not null | 作業開始時刻 | `WorkContext.startedAtEpochMillis` | Unix epoch milliseconds |

Cardinality:

- 正常時は exactly 1 row。
- 0 rows は Android 側では contract mismatch として扱う。
- multiple rows は Android 側では contract mismatch として扱う。

## Rule Bundle Contract

```sql
api.fn_get_rule_bundle(p_work_id text)
returns table (
    rule_version text,
    report_id text,
    effective_at_epoch_millis bigint,
    target_equipment_types text[]
)
```

Android は `work_id` で rule bundle を取得する。
`report_id` 単独では作業対象を一意にできないため、Function input には使わない。

| column | PostgreSQL type | nullable | meaning | Kotlin field | notes |
| --- | --- | --- | --- | --- | --- |
| `rule_version` | `text` | not null | rule bundle の version | `RuleBundle.ruleVersion` | DB 側で比較可能な安定文字列にする |
| `report_id` | `text` | not null | rule が対象にする帳票 ID | `RuleBundle.reportId` | `WorkContext.reportId` と一致すること |
| `effective_at_epoch_millis` | `bigint` | not null | rule 有効時刻 | `RuleBundle.effectiveAtEpochMillis` | Unix epoch milliseconds |
| `target_equipment_types` | `text[]` | not null | 判定対象にする設備種別 | `RuleBundle.targetEquipmentTypes` | empty array は禁止 |

Cardinality:

- 正常時は exactly 1 row。
- 0 rows / multiple rows は contract mismatch。

## Equipment Snapshot Contract

```sql
api.fn_get_equipment_snapshot(p_work_id text)
returns table (
    snapshot_version text,
    captured_at_epoch_millis bigint,
    equipment_id text,
    epc text,
    equipment_type text,
    display_name text
)
```

Android は `work_id` で設備 master snapshot を取得する。
Function は設備 1 件につき 1 row を返す。
snapshot metadata は全 row で同一値を返す。

| column | PostgreSQL type | nullable | meaning | Kotlin field | notes |
| --- | --- | --- | --- | --- | --- |
| `snapshot_version` | `text` | not null | master snapshot version | `EquipmentSnapshot.snapshotVersion` | 全 row で同一値 |
| `captured_at_epoch_millis` | `bigint` | not null | snapshot 取得時刻 | `EquipmentSnapshot.capturedAtEpochMillis` | 全 row で同一値 |
| `equipment_id` | `text` | not null | 設備 ID | `EquipmentRecord.equipmentId` | DB 内で設備を特定する ID |
| `epc` | `text` | not null | RFID EPC | `EquipmentRecord.epc` | trim + uppercase の正規化済み値 |
| `equipment_type` | `text` | not null | 設備種別 | `EquipmentRecord.equipmentType` | `target_equipment_types` と照合される |
| `display_name` | `text` | not null | 表示名 | `EquipmentRecord.displayName` | UI 表示や調査用 |

Cardinality:

- 正常時は 1 or more rows。
- empty snapshot は許可しない。
- Kotlin は row ordering を仮定しない。

## Read Result Registration Function

```sql
api.fn_register_read_result_bundle(p_bundle jsonb)
returns table (
    success boolean,
    duplicate boolean,
    accepted_session_id text,
    result_id uuid,
    failure_kind text,
    error_code text,
    message text
)
```

Function は exactly 1 row を返す。
入力検証失敗や業務的な reject は exception ではなく result row で返す。
通信断、timeout、DB 接続不可、DB 内部障害は SQL exception として扱われる。

### Return Columns

| column | PostgreSQL type | nullable | meaning | Kotlin field | notes |
| --- | --- | --- | --- | --- | --- |
| `success` | `boolean` | not null | 登録が成功扱いか | `ReadResultRegistrationResult.Success` / `Failure` | `true` なら success |
| `duplicate` | `boolean` | not null | 同じ idempotency key の既登録か | `Success.duplicate` | duplicate success でも `success=true` |
| `accepted_session_id` | `text` | nullable | 受理した session ID | `Success.acceptedSessionId` | success の場合 not null |
| `result_id` | `uuid` | nullable | DB 側 result aggregate ID | `Success.resultId` | success の場合 not null。Kotlin では String として保持 |
| `failure_kind` | `text` | nullable | failure category | `Failure.kind` | failure の場合 not null |
| `error_code` | `text` | nullable | machine-readable error code | `Failure.errorCode` | failure の場合 not null |
| `message` | `text` | nullable | operator / log 向け message | `Failure.message` | failure の場合 not null を推奨 |

`failure_kind` values:

| DB value | Kotlin mapping | retry queue |
| --- | --- | --- |
| `retryable` | `RegistrationFailureKind.Retryable` | queue に積む |
| `configuration` | `RegistrationFailureKind.Configuration` | queue に積まない |
| `contract` | `RegistrationFailureKind.Contract` | queue に積まない |
| `unknown` | `RegistrationFailureKind.Unknown` | queue に積まない |

## Registration JSONB Schema

`p_bundle` は JSON object でなければならない。
Unknown top-level field と unknown tag field は `success=false`, `failure_kind='contract'`, `error_code='unknown_field'` で reject する。

### Top-Level Fields

| field | JSON type | required | nullable | meaning | Kotlin source | validation |
| --- | --- | --- | --- | --- | --- | --- |
| `schemaVersion` | number | yes | no | JSON schema version | encoder constant | must be `1` |
| `sessionId` | string | yes | no | Android read session ID | `sessionId` | non-empty |
| `registeredAtEpochMillis` | number | yes | no | 登録要求時刻 | `registeredAtEpochMillis` | Unix epoch milliseconds, `>= 0` |
| `deviceId` | string | yes | no | Android terminal ID | `deviceId` | non-empty |
| `readerType` | string | yes | no | reader type | `readerType` | current value `RP902` |
| `workId` | string | yes | no | work context ID | `workId` | must exist |
| `reportId` | string | yes | no | report ID | `reportId` | must match work context |
| `operatorId` | string | yes | yes | operator ID | `operatorId` | null allowed |
| `ruleVersion` | string | yes | no | rule bundle version used by Android | `ruleVersion` | must match available rule snapshot policy |
| `equipmentSnapshotVersion` | string | yes | no | equipment snapshot version used by Android | `equipmentSnapshotVersion` | must match available master snapshot policy |
| `tags` | array | yes | no | 判定済み tag list | `tags` | non-empty |

### Tag Fields

| field | JSON type | required | nullable | meaning | Kotlin source | validation |
| --- | --- | --- | --- | --- | --- | --- |
| `epc` | string | yes | no | normalized EPC | `ReadResultTag.epc` | trim + uppercase 済み、non-empty |
| `firstSeenAtEpochMillis` | number | yes | no | 初回読取時刻 | `firstSeenAtEpochMillis` | Unix epoch milliseconds |
| `lastSeenAtEpochMillis` | number | yes | no | 最終読取時刻 | `lastSeenAtEpochMillis` | `>= firstSeenAtEpochMillis` |
| `readCount` | number | yes | no | session 内読取回数 | `readCount` | integer, `>= 1` |
| `judgementStatus` | string | yes | no | Android 端末内判定結果 | `judgementStatus.name` | enum below |
| `judgementReasonCode` | string | yes | yes | 判定理由 code | `judgementReasonCode` | `Accepted` は null、その他は non-empty 推奨 |

`judgementStatus` values:

| JSON value | meaning |
| --- | --- |
| `Accepted` | 登録対象として受理 |
| `Excluded` | rule により対象外 |
| `Ng` | 不一致または異常判定 |

`judgementReasonCode` formal values currently used by Android:

| value | allowed with | meaning |
| --- | --- | --- |
| `null` | `Accepted` | 理由なし |
| `equipment_not_found` | `Ng` | EPC に対応する設備 master がない |
| `equipment_type_not_allowed` | `Excluded` | rule の対象設備種別ではない |

DB は未知の `judgementReasonCode` を保存してよいが、未知値を reject する場合は `failure_kind='contract'` とする。

### Registration Error Codes

`api.fn_register_read_result_bundle` は入力検証・業務 reject を exception ではなく result row で返す。
この契約で定義する `error_code` は以下とする。

| error_code | failure_kind | meaning |
| --- | --- | --- |
| `unknown_field` | `contract` | top-level または tag object に未知 field がある |
| `missing_required_field` | `contract` | required field がない |
| `invalid_type` | `contract` | JSON type、空文字、整数、正規化 EPC などの型・形式違反 |
| `invalid_schema_version` | `contract` | `schemaVersion` が `1` ではない |
| `invalid_judgement_status` | `contract` | `judgementStatus` または `judgementReasonCode` の整合性違反 |
| `invalid_time_range` | `contract` | `lastSeenAtEpochMillis < firstSeenAtEpochMillis` |
| `empty_tags` | `contract` | `tags` が空 |
| `duplicate_epc` | `contract` | 同一 bundle 内に同じ EPC が複数存在する |
| `work_not_found` | `contract` | `workId` が存在しない |
| `report_mismatch` | `contract` | `reportId` が work context と一致しない |
| `rule_bundle_not_found` | `contract` | `ruleVersion` が active rule bundle と一致しない |
| `empty_equipment_snapshot` | `contract` | `equipmentSnapshotVersion` が active non-empty snapshot と一致しない |
| `idempotency_payload_mismatch` | `contract` | 同一 `(deviceId, sessionId)` に異なる payload が送信された |
| `device_not_assigned` | `configuration` | `deviceId` が未登録、disabled、または submitted workId に active assignment されていない |

## Idempotency Contract

DB の冪等性 key は `(deviceId, sessionId)` とする。

理由:

- `sessionId` は Android 端末内の session ID であり、端末間での一意性は保証しない。
- `deviceId` と組み合わせると、端末ごとの再送を同じ登録単位として扱える。
- Kotlin の `ReadResultRegistrationBundle.idempotencyKey` は local queue 用の文字列表現で、DB では別々の column として保持する。

Rules:

- 初回登録成功時、DB は `(device_id, session_id)` に unique constraint を持つ result aggregate を作る。
- 同一 `(deviceId, sessionId)` かつ payload が同等なら duplicate success として返す。
- duplicate success は `success=true`, `duplicate=true`, `accepted_session_id=sessionId`, `result_id=<existing uuid>` とする。
- 同一 key で payload が異なる場合は `success=false`, `failure_kind='contract'`, `error_code='idempotency_payload_mismatch'` とする。
- Kotlin `PendingWriteQueue` は `bundle.idempotencyKey` を local queue key として使う。
- retryable failure の再送は、初回成功または duplicate success のどちらでも `RegistrationState.Completed(sessionId)` に進めてよい。

## Failure Mapping Contract

### Function Result Row

| DB result | Kotlin result | queue |
| --- | --- | --- |
| `success=true`, `duplicate=false` | `ReadResultRegistrationResult.Success(duplicate=false, ...)` | remove |
| `success=true`, `duplicate=true` | `ReadResultRegistrationResult.Success(duplicate=true, ...)` | remove |
| `success=false`, `failure_kind='retryable'` | `Failure(kind=Retryable)` | enqueue |
| `success=false`, `failure_kind='configuration'` | `Failure(kind=Configuration)` | do not enqueue |
| `success=false`, `failure_kind='contract'` | `Failure(kind=Contract)` | do not enqueue |
| `success=false`, `failure_kind='unknown'` | `Failure(kind=Unknown)` | do not enqueue |

### SQL Exception

| SQLSTATE / exception | Kotlin failure kind | notes |
| --- | --- | --- |
| class `08` connection exception | `Retryable` | network / server availability |
| `SQLTimeoutException` / socket timeout | `Retryable` | retry later |
| class `28` auth failure | `Configuration` | credential / role issue |
| class `22` data exception | `Contract` | invalid data shape |
| class `42` syntax / undefined object | `Contract` | missing function / wrong column |
| `P0002`, `P0003`, `42883`, `42P01`, `42703`, `42804`, `21000`, `0A000` | `Contract` | explicit contract mismatch |
| other / no SQLSTATE | `Unknown` | do not queue until classified |

## Kotlin Mapping Table

| Contract element | Kotlin location |
| --- | --- |
| Function names | `PostgresSchema` |
| SQL statements | `PostgresSqlStatements` |
| Work context row | `PostgresRowMappers.workContext` |
| Rule bundle row | `PostgresRowMappers.ruleBundle` |
| Equipment snapshot rows | `PostgresRowMappers.equipmentSnapshot` |
| Registration JSONB | `ReadResultBundleJsonEncoder` |
| Registration result row | `PostgresRowMappers.registrationResult` |
| SQL exception mapping | `PostgresErrorMapper` |
| Local pending key | `ReadResultRegistrationBundle.idempotencyKey` |

## Current Status

Android 側はこの契約に合わせて compile / unit test / debug build 済み。
DB 側の starter implementation は `docs/architecture/postgresql` 配下に分割して配置済み。
live PostgreSQL 接続、証明書配布、credential 配布、本番 import job は未実施。
