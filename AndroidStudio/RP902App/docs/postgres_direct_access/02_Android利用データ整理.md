# 02 Android 利用データ整理

Android が PostgreSQL から取得または登録するデータを整理します。

## 取得データ

| データ | 目的 | Android 側モデル |
| --- | --- | --- |
| work context | 作業対象の確定 | `WorkContext` |
| rule bundle | 帳票ルール判定 | `RuleBundle` |
| equipment snapshot | 設備照合 | `EquipmentSnapshot` |

## 登録データ

| データ | 目的 | Android 側モデル |
| --- | --- | --- |
| read result registration bundle | 判定済み結果の登録 | `ReadResultRegistrationBundle` |
| pending write | 登録失敗後の再実行 | `PendingWrite` |

## 登録 bundle の現行項目

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

端末内判定の本実装後、判定結果、理由コード、rule/master snapshot version を追加する。
