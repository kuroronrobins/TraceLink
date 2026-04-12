# In-memory implementation inventory

最終確認日: 2026-04-12

## 結論

現在のアプリには、意図的な in-memory 実装が複数残っている。これは fake reader default の開発段階では妥当だが、送信失敗 payload、読み取り中 session、設定値は本番前に永続化方針を決める必要がある。

ここでいう in-memory とは、アプリのプロセスが生きている間だけ保持され、アプリ終了・再起動・OS による kill で消える状態を指す。

## 一覧

| 優先度 | 対応クラス / ファイル | 保持内容 | 消失時の影響 | 今の妥当性 | 推奨対応 |
| --- | --- | --- | --- | --- | --- |
| 高 | `InMemoryUploadRetryQueue` / `data/upload/InMemoryUploadRetryQueue.kt` | upload 失敗 payload、attempt count、最後の失敗理由 | 再起動すると未送信データが消え、再送できない | 開発段階では可 | 本番前に Room または file backed queue へ差し替える |
| 高 | `InventorySession` / `domain/inventory/InventorySession.kt` | 現在 session の EPC、first/last seen、read count | 読取中にアプリが落ちると session 全体が消える | 開発段階では可 | 長時間運用やオフライン運用前に session draft を永続化する |
| 中 | `InMemoryReaderSettingsRepository` / `data/settings/InMemoryReaderSettingsRepository.kt` | gateway mode、RP902 Bluetooth MAC | 再起動で fake mode と既定 MAC に戻る。ユーザーが別 MAC に変えた場合は再入力が必要 | 現在は 1 台固定 default MAC のため可 | Settings を DataStore へ永続化する |
| 中 | `InMemoryEventLogStore` / `data/log/InMemoryEventLogStore.kt` | structured log 最新 200 件 | 再起動で現場の切り分けログが消える | 開発段階では可 | 実機トラブル解析が必要なら file / Room / log export を追加する |
| 中 | `InventoryRepositoryState` / `domain/inventory/InventoryRepository.kt` | UI に見せる connection / inventory / upload / tags / logs / pendingUploads の投影 | 再起動で画面状態が初期化される | projection なので妥当 | 直接永続化せず、元データ側を永続化して再構築する |
| 低 | `InMemoryReaderRuntimeStateRepository` / `data/reader/bluetooth/InMemoryReaderRuntimeStateRepository.kt` | permission と Bluetooth 状態の最新 snapshot | 再起動後に再取得が必要 | 妥当。Android permission は OS が真の情報源 | 永続化しない。起動時・画面復帰時に再評価する |
| 低 | `ConfigurableReaderGateway` / `data/reader/ConfigurableReaderGateway.kt` | active gateway、gateway mode に応じた接続経路、preflight 結果の現在値 | 再起動で切断状態から始まる | 妥当 | 永続化対象ではない。設定値だけ保存する |
| 低 | `RealRp902Gateway` / `data/reader/real/RealRp902Gateway.kt` | 接続状態、callback count、last callback timestamp などの診断状態 | 再起動で診断値が消える | 妥当 | 必要なら EventLogStore 側へ永続ログとして残す |
| 低 | `FakeReaderGateway` / `data/reader/FakeReaderGateway.kt` | fake 接続状態、fake inventory job | fake 動作が初期化される | 妥当 | 永続化不要 |
| 低 | `SettingsViewModel` / `ui/settings/SettingsViewModel.kt` | 入力中の MAC 文字列や画面表示用 error | 画面再生成で入力途中の値が失われる可能性 | 現時点では許容 | Settings repository 永続化後も、入力途中 state は UI 都合として扱う |

## 重点項目

### UploadRetryQueue

送信失敗時の payload を保持する最重要の in-memory 実装である。現在は `sessionId` を pending upload id として使い、同じ session の失敗を重複登録しない設計になっている。

本番で network failure や backend failure が起きる場合、この queue が消えると「読んだが送れなかったデータ」が復元できない。サーバー側は受け取っていないデータを復元できないため、サーバー側だけでは吸収できない。

推奨:

- 最初の本番候補では Room を第一候補にする。
- payload JSON をそのまま保存する file backed queue でも初期実装は可能。
- 冪等性キーは `sessionId` または別の upload id としてサーバー契約に合わせる。

### InventorySession

同じ session 内で EPC を重複排除し、read count と first/last seen を更新する純粋な domain logic である。現在は `linkedMapOf` で保持している。

読取中にアプリが落ちた場合、未 upload の session は失われる。短時間の実機検証では許容できるが、現場運用で「読取中断しても復元したい」なら永続化が必要。

推奨:

- 「Start した session を一時保存し、Upload completed で完了扱いにする」形を検討する。
- 既存の `InventorySession` は純粋ロジックとして残し、Repository で永続 storage と同期する。

### Settings

現在は default MAC `DC:0D:30:DA:0F:3C` が入るため、1 台固定運用では再起動後も実質的な手入力負担は小さい。ただし、別 reader の MAC に変更した場合は再起動で戻る。

推奨:

- 複数台運用や現場端末配布前に DataStore へ差し替える。
- 保存対象は gateway mode と reader Bluetooth MAC から始める。

### Logs

ログは現場切り分けに有効だが、現在は最新 200 件だけを in-memory で保持している。クラッシュ後にログが消えるため、実機トラブル解析では不足する可能性がある。

推奨:

- 実機試験フェーズでは Logcat と structured log 画面を併用する。
- 本番前に file export または Room 保存を検討する。

## 今すぐ対応不要なもの

以下は runtime snapshot または UI projection であり、永続化すると逆に古い状態を誤表示する危険がある。

- Bluetooth enabled / disabled の snapshot
- Android runtime permission の granted snapshot
- reader connection state
- inventory running state
- fake reader の job

これらは保存するのではなく、起動時・画面復帰時・操作直前に再評価する方が安全である。

## 推奨対応順

1. upload retry queue を永続化する。
2. inventory session draft を永続化するか、運用上「途中 session は失われる」と明記する。
3. reader settings を DataStore に保存する。
4. structured log の file / DB 保存または export を追加する。
5. runtime snapshot は永続化しない方針を維持する。
