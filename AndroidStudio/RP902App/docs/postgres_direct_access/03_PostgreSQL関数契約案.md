# 03 PostgreSQL 関数契約案

Android は PostgreSQL table を直接前提にせず、View / Function を通して利用する。

## 候補

| 目的 | PostgreSQL 側境界 | Android 側 |
| --- | --- | --- |
| 作業対象取得 | `fn_get_work_context` | `WorkContextRepository.resolveCurrentWorkContext()` |
| 帳票ルール取得 | `fn_get_rule_bundle` | `RuleRepository.fetchRuleBundle()` |
| 設備マスタ取得 | `fn_get_equipment_snapshot` | `EquipmentMasterRepository.fetchEquipmentSnapshot()` |
| 読取結果登録 | `fn_register_read_result_bundle` | `ReadResultRepository.register()` |
| 登録結果参照 | `vw_registration_result` | `ReadResultRepository` 実装内 |
| XCgate 参照 | `vw_final_result_for_xcgate` | XCgate |

## 設計ルール

- Android が SQL 文字列を組み立てる範囲を最小にする。
- Function 側で入力検証と冪等性を担保する。
- 制約違反は Android 側で扱えるエラーコードに変換する。
- 監査用に registered device、session、function 実行時刻を保持する。
