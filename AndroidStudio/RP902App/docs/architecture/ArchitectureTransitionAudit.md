# アーキテクチャ移行監査

## 監査概要

旧方針を示す語彙を、リポジトリ全体から確認した。対象語は `backend`、`server`、`upload`、`UploadRepository`、`FakeUploadRepository`、`UploadRetryQueue`、`InventoryUploadPayload`、`uploadSession`、`retryPendingUploads`、`BackendContract`、`central server`、`API endpoint`、`中央サーバ`、`最終判定`、`送信` などである。

vendor SDK の `transport` や callback の `payload` は RP902 SDK の文脈であり、旧 backend/upload 方針とは別扱いにした。

## 旧方針の残存箇所と対応

| file path | 残っていた旧表現 | 新方針での修正 | 区分 |
| --- | --- | --- | --- |
| `README.md` | backend service / upload / Backend Responsibilities | Android が PostgreSQL から rule/master を取得し、端末内判定後に結果登録する説明へ更新 | docs |
| `docs/architecture/BackendContract.md` | backend contract / final judgement by backend / API endpoint | `PostgreSQLAccessContract.md` に rename し、View / Function / 制約の契約へ更新 | docs / naming |
| `docs/backend_handover/*` | backend handover / API 契約 / サーバー責務 | `docs/postgres_direct_access/*` に rename し、PostgreSQL 直接アクセス引き継ぎ資料へ更新 | docs / naming |
| `docs/onboarding/00_はじめに.md` | backend へ送信 / upload / retry queue | PostgreSQL 登録、pending write、端末内判定へ更新 | docs |
| `docs/onboarding/01_全体構成.md` | `domain/upload`、`data/upload`、`UploadRepository` | `domain/readresult`、`data/readresult`、`ReadResultRepository` へ更新 | docs |
| `docs/onboarding/02_画面と機能の説明.md` | Upload ボタン、UploadState、UploadRetryQueue | Register ボタン、RegistrationState、PendingWriteQueue へ更新 | docs |
| `docs/onboarding/03_処理フロー.md` | Upload 詳細フロー、backend 送信 | work context / rule / master / local judgement / register flow へ更新 | docs |
| `docs/onboarding/04_クラス責務一覧.md` | UploadRepository / FakeUploadRepository / UploadRetryQueue | ReadResultRepository / FakeReadResultRepository / PendingWriteQueue へ更新 | docs |
| `docs/onboarding/05_データの流れ.md` | backend upload payload の流れ | read result registration bundle と PostgreSQL function の流れへ更新 | docs |
| `docs/onboarding/06_状態遷移.md` | UploadState | RegistrationState と pending write へ更新 | docs |
| `docs/onboarding/07_用語集.md` | retry queue / upload repository | pending write queue / read result repository へ更新 | docs |
| `docs/onboarding/08_実機デバッグ手順.md` | Upload が怪しいとき | 結果登録と pending write の切り分けへ更新 | docs |
| `docs/onboarding/09_変更ガイド.md` | upload を本物サーバーへ差し替える | PostgreSQL function 実装を追加する手順へ更新 | docs |
| `docs/onboarding/10_FAQ.md` | upload は本物か / backend API 確定 | fake read result repository と PostgreSQL 契約確定へ更新 | docs |
| `docs/product/*` | backend upload / final judgement remains on backend | Android 端末内判定と PostgreSQL 結果登録へ更新 | docs |
| `docs/maintenance/*` | upload retry queue | pending write queue へ更新 | docs |
| `docs/ai/CODEX_PROMPT_01.md` | server upload / upload payload | PostgreSQL result registration へ更新 | docs |
| `app/src/main/java/.../domain/upload/*` | upload package and type names | `domain/readresult/*` へ rename | code / naming |
| `app/src/main/java/.../data/upload/*` | fake upload / upload retry queue | `data/readresult/*` へ rename | code / naming |
| `InventoryRepository` | `uploadSession()` / `retryPendingUploads()` | `registerCurrentSessionResults()` / `retryPendingWrites()` へ rename | code / naming |
| `InventoryUiState` / `InventoryScreen` | Upload 表示、Queued 表示 | Registration / Pending writes 表示へ更新 | code |
| `AppLogCategory.Upload` | upload log category | `ResultRegistration` へ rename | code / naming |
| `システム図/*.drawio` | 中央サーバー、サーバー送信、backend judgement | Android / PostgreSQL / XCgate のみの単方向図へ更新 | diagram |

## Rename / terminology map

| old name | proposed new name | rename reason | affected files | should rename |
| --- | --- | --- | --- | --- |
| `domain/upload` | `domain/readresult` | upload ではなく読取結果登録の責務にする | Kotlin domain package | now |
| `data/upload` | `data/readresult` | fake 実装も登録先として扱う | Kotlin data package | now |
| `UploadRepository` | `ReadResultRepository` | 通信方式ではなく保存対象で命名する | domain/data/tests/docs | now |
| `FakeUploadRepository` | `FakeReadResultRepository` | fake の役割を結果登録先にする | data/tests/docs | now |
| `UploadRetryQueue` | `PendingWriteQueue` | 登録失敗の保留書き込みを表す | domain/data/tests/docs | now |
| `InMemoryUploadRetryQueue` | `InMemoryPendingWriteQueue` | 永続化前の pending write queue と明示する | data/tests/docs | now |
| `InventoryUploadPayload` | `ReadResultRegistrationBundle` | PostgreSQL function へ渡す登録単位にする | domain/data/tests/docs | now |
| `PendingUpload` | `PendingWrite` | 送信ではなく DB 書き込み保留を表す | domain/data/tests/docs | now |
| `UploadState` | `RegistrationState` | UI 状態を登録処理として表す | domain/ui/tests/docs | now |
| `uploadSession()` | `registerCurrentSessionResults()` | 現在 session の結果登録を表す | Repository/ViewModel/UI/tests | now |
| `retryPendingUploads()` | `retryPendingWrites()` | 保留書き込みの再実行を表す | Repository/ViewModel/UI/tests | now |
| `BackendContract.md` | `PostgreSQLAccessContract.md` | backend 専用アプリ廃止を反映 | architecture docs | now |
| `backend_handover` | `postgres_direct_access` | 引き継ぎ対象を PostgreSQL 直接アクセスへ変更 | docs directory | now |
| `InventoryRepository` | `InventoryWorkflowRepository` | reader/session/result を束ねる責務が広い | code/docs | later |
| `InventorySession` | `ReadSessionAccumulator` | session 内重複除去に責務を絞るなら明確になる | code/docs | later |

## Migration note

旧方針の文書名や class 名は、混乱を防ぐため原則として残さない。過去判断の履歴が必要な場合は、この監査文書または明示した migration note に隔離する。
