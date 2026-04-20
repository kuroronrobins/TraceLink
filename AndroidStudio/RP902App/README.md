# TraceLink RP902 App

TraceLink RP902 App は、Unitech RP902 で RFID を読み取り、Android 端末内で判定し、PostgreSQL に読取結果を登録する Android アプリです。

## 現行方針

- 専用中間アプリケーションは置きません。
- 実行時の主要要素は Android アプリ、PostgreSQL、XCgate です。
- Android アプリは PostgreSQL から作業対象、帳票ルール、設備マスタを取得します。
- Android アプリは session 内重複除去と端末内判定を行います。
- Android アプリは判定済み読取結果を PostgreSQL function 経由で登録します。
- PostgreSQL は Android 用 View / Function / 制約を持つ整合性ガード層です。
- XCgate は PostgreSQL の final result view を参照します。

## App Responsibilities

- RP902 へ接続する。
- RFID inventory を開始・停止する。
- 同一 session 内の EPC 重複を除去する。
- PostgreSQL から rule/master/context を取得する差し替え点を持つ。
- 端末内判定の差し替え点を持つ。
- 読取結果登録の状態と pending write を見える状態にする。
- 接続、inventory、結果登録、エラーの構造化ログを表示する。

## Development Direction

- Kotlin only.
- Jetpack Compose for new UI.
- ViewModel + Repository + unidirectional data flow.
- UI から RP902 vendor SDK や PostgreSQL 接続実装を直接呼ばない。
- RP902 vendor SDK code は adapter boundary に閉じ込める。
- PostgreSQL 実接続は View / Function / 制約の契約が固まってから実装する。
- 実接続前は interface と fake implementation で進める。
- 最終的な RP902 挙動は実 Android 端末で検証する。

## Reader Settings

The app still starts in fake reader mode by default. For the current one-reader operation, the RP902 Bluetooth MAC field is prefilled with `DC:0D:30:DA:0F:3C` so hardware testing does not require typing the address every time.

This is only a default value. The Settings screen remains editable, and the MAC address can be changed later if a different RP902 reader is used.

## Architecture Docs

- `docs/architecture/TraceLink_CurrentArchitecture.md`: 現行方針の基準文書。
- `docs/architecture/PostgreSQLAccessContract.md`: Android と PostgreSQL の View / Function 境界。
- `docs/architecture/ArchitectureTransitionAudit.md`: 旧方針から新方針への監査と rename map。
- `docs/architecture/ResponsibilitySeparationPlan.md`: 今後の責務分離案。

## Vendor SDK Assets

Unitech SDK files are stored under `vendor/unitech/` with separate roles:

- `vendor/unitech/runtime/`: stable build inputs used by `app/build.gradle.kts`.
- `vendor/unitech/docs/`: Javadoc and vendor reference documents.
- `vendor/unitech/upstream/`: preserved original SDK distribution for traceability.

The app must only depend on `vendor/unitech/runtime/`. Sample apps, APKs, Xamarin files, Gradle wrappers, and sample signing material remain isolated under `upstream/` and are not part of the app dependency path.

Before updating or redistributing vendor files, confirm the Unitech license terms. For SDK updates, replace the upstream drop, copy only required runtime binaries into `runtime/`, update docs if file names or API findings change, then run build, unit tests, and lint.

## Maintenance Notes

Operational risk notes are kept under `docs/maintenance/`.

- `docs/maintenance/16kb_alignment_assessment.md`: current `Aligned16KB` lint warning and vendor native library risk.
- `docs/maintenance/signing_and_keystore_audit.md`: app signing path and confirmation that sample keystores are not used.
- `docs/maintenance/in_memory_inventory.md`: in-memory stores that must be revisited before production.
- `docs/maintenance/runtime_risks_summary.md`: prioritized summary of runtime and release risks.
