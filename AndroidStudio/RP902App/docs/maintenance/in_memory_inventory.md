# In-memory implementation inventory

最終更新日: 2026-04-20

## 結論

現在のアプリには、意図的な in-memory 実装が複数残っている。fake reader default の開発段階では妥当だが、pending write、読み取り中 session、設定値は本番前に永続化方針を決める必要がある。

## 一覧

| 優先度 | 対応クラス / ファイル | 保持内容 | 消失時の影響 | 推奨対応 |
| --- | --- | --- | --- | --- |
| 高 | `InMemoryPendingWriteQueue` / `data/readresult/InMemoryPendingWriteQueue.kt` | 登録失敗 bundle、attempt count、最後の失敗理由 | 再起動すると PostgreSQL 未登録データを再実行できない | 本番前に Room などへ差し替える |
| 高 | `InventorySession` / `domain/inventory/InventorySession.kt` | 現在 session の EPC、first/last seen、read count | 読取中にアプリが落ちると session 全体が消える | 長時間運用前に session draft 永続化を検討する |
| 中 | `InMemoryReaderSettingsRepository` / `data/settings/InMemoryReaderSettingsRepository.kt` | gateway mode、RP902 Bluetooth MAC | 再起動で fake mode と既定 MAC に戻る | DataStore へ差し替える |
| 中 | `InMemoryEventLogStore` / `data/log/InMemoryEventLogStore.kt` | structured log 最新 200 件 | 再起動で現場の切り分けログが消える | file / Room / export を検討する |
| 中 | `InventoryRepositoryState` / `domain/inventory/InventoryRepository.kt` | UI に見せる connection / inventory / registration / tags / logs / pendingWrites の投影 | 再起動で画面状態が初期化される | 直接永続化せず、元データ側を永続化して再構築する |

## 重点項目

### PendingWriteQueue

PostgreSQL function への登録に失敗した bundle を保持する最重要の in-memory 実装である。現在は `sessionId` を pending write id として使い、同じ session の失敗を重複登録しない。

本番で network failure や PostgreSQL failure が起きる場合、この queue が消えると「読めたが登録できなかったデータ」が復元できない。

推奨:

- 最初の本番候補では Room を第一候補にする。
- bundle JSON を保存する file backed queue でも初期実装は可能。
- 冪等性キーは PostgreSQL function 契約に合わせる。

### InventorySession

同じ session 内で EPC を重複排除し、read count と first/last seen を更新する純粋な domain logic である。

読取中にアプリが落ちた場合、未登録の session は失われる。短時間の実機検証では許容できるが、現場運用で「読取中断しても復元したい」なら永続化が必要。

## 今すぐ対応不要なもの

- Bluetooth enabled / disabled の snapshot
- Android runtime permission の granted snapshot
- reader connection state
- inventory running state
- fake reader の job

これらは保存するのではなく、起動時・画面復帰時・操作直前に再評価する方が安全である。

## 推奨対応順

1. pending write queue を永続化する。
2. inventory session draft を永続化するか、運用上「途中 session は失われる」と明記する。
3. reader settings を DataStore に保存する。
4. structured log の file / DB 保存または export を追加する。
