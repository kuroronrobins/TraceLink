# PostgreSQL Implementation Notes

この directory は `PostgreSQLAccessContract.md` に定義された Android ↔ PostgreSQL 契約の DB 側実装である。
Android が参照してよい public boundary は `api.fn_*` だけであり、内部 table は `tracelink_internal` schema に隔離する。

## 適用順

`psql` から `docs/architecture/PostgreSQLContractStubs.sql` を実行すると、実装本体だけを順番に読み込む。

```sql
\ir PostgreSQLContractStubs.sql
```

検証用 data と manual checks は任意で実行する。

```sql
\ir postgresql/40_seed_test_data.sql
\ir postgresql/50_verification_queries.sql
```

real DB / local container での検証手順は `VALIDATION_PLAN.md` を参照する。
Android 用 least-privilege role は、SQL 実装を適用してから `60_role_hardening_template.sql` で設定する。

## File Roles

| file | role |
| --- | --- |
| `00_schema.sql` | `api` / `tracelink_internal` schema と `pgcrypto` extension を作成する |
| `10_internal_tables.sql` | device assignment、work context、rule bundle、equipment snapshot、read result、audit の内部 table と constraint を作成する |
| `20_helpers.sql` | epoch millis、payload hash、registration result row、JSONB validation、audit helper を定義する |
| `30_api_functions.sql` | Android-facing `api.fn_*` を実装する |
| `40_seed_test_data.sql` | manual verification 用の最小 seed data を投入する |
| `50_verification_queries.sql` | normal / duplicate / payload mismatch / validation failure の確認 query をまとめる |
| `60_role_hardening_template.sql` | Android role と SECURITY DEFINER owner の権限 template |
| `70_role_validation_queries.sql` | Android role が `api.fn_*` だけ使えることを確認する query |
| `compose.postgres-smoke.yml` | local smoke test 用 PostgreSQL container |
| `VALIDATION_PLAN.md` | DB / role / Android smoke test の手順と期待結果 |

## Internal Storage Summary

| table | purpose |
| --- | --- |
| `tracelink_internal.device_registry` | Android device の登録状態と有効/無効状態 |
| `tracelink_internal.work_context` | 作業対象、帳票 ID、operator、開始時刻 |
| `tracelink_internal.device_work_assignment` | deviceId から active work context への割当 |
| `tracelink_internal.rule_bundle` | workId に紐づく active rule bundle |
| `tracelink_internal.equipment_snapshot` | workId に紐づく active equipment snapshot metadata |
| `tracelink_internal.equipment_snapshot_record` | snapshot 内の equipment master records |
| `tracelink_internal.read_result_session` | 登録単位。`(device_id, session_id)` で idempotency を強制する |
| `tracelink_internal.read_result_tag` | 登録 bundle 内の tag 明細 |
| `tracelink_internal.read_result_audit` | success / duplicate / failure の監査履歴 |

## Public Function Behavior

`api.fn_*` は `SECURITY DEFINER` と固定 `search_path` を使う。
これは Android 用 DB role に内部 table 権限を与えず、`execute` 権限だけで contract boundary を保つためである。

| function | behavior |
| --- | --- |
| `api.fn_get_active_work_context(p_device_id text)` | active device assignment を exactly 1 row で返す。0 件は `device_not_assigned`、複数候補は `multiple_active_work_contexts` として SQL exception |
| `api.fn_get_rule_bundle(p_work_id text)` | active rule bundle を exactly 1 row で返す。0 件は `rule_bundle_not_found`、複数候補は `multiple_rule_bundles` として SQL exception |
| `api.fn_get_equipment_snapshot(p_work_id text)` | active non-empty snapshot を 1 row per equipment で返す。0 件または空 snapshot は `empty_equipment_snapshot` として SQL exception |
| `api.fn_register_read_result_bundle(p_bundle jsonb)` | validation / business rejection / duplicate success / success を exactly 1 result row で返す |

## Idempotency

DB 側の idempotency key は `(device_id, session_id)` である。

- 初回登録は `read_result_session` と `read_result_tag` に保存し、`success=true`, `duplicate=false` を返す。
- 同一 key かつ同一 payload hash の retry は `success=true`, `duplicate=true` を返す。
- 同一 key かつ異なる payload hash は `success=false`, `failure_kind='contract'`, `error_code='idempotency_payload_mismatch'` を返す。

payload hash は `jsonb::text` に対する SHA-256 で計算する。
PostgreSQL の `jsonb` は object key order を正規化するため、JSON object の field order 差分は payload mismatch にならない。

## Error Codes

`api.fn_register_read_result_bundle` は以下の `failure_kind` を返す。

| failure_kind | meaning |
| --- | --- |
| `retryable` | 再送すれば成功し得る一時障害 |
| `configuration` | device assignment や credential など運用設定の問題 |
| `contract` | Android ↔ PostgreSQL contract に反する入力または DB state |
| `unknown` | 分類不能な障害 |

実装済みの `error_code` は以下の通り。

| error_code | failure_kind | condition |
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

## Remaining DB Decisions

- production では Android 用 DB role に `api.fn_*` の `execute` だけを許可し、`tracelink_internal` への direct access を禁止する。
- `SECURITY DEFINER` を使うため、production では `public` schema の `CREATE` 権限を明示的に revoke する。
- `pgcrypto` extension の有効化は managed PostgreSQL の権限設計に合わせて事前確認する。
- rule / equipment import の本番 ETL は別途設計する。
