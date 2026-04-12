# Runtime risks summary

最終確認日: 2026-04-12

## 今回の3テーマ要約

| テーマ | 結論 | 現時点の危険度 | 放置してよい期間感 | 本番前必須 | 依存 |
| --- | --- | --- | --- | --- | --- |
| 16 KB alignment warning | Unitech AAR 由来の `arm64-v8a/libJNISTUHFL.so` が `Aligned16KB` warning 対象 | 中 | 開発・実機切り分け中は可 | はい | vendor 依存 |
| sample keystore | 現在の app build では使っていない。debug は標準 debug keystore、release は未設定 | 低 | release signing 設定前までは可 | はい | app / CI 運用依存 |
| in-memory 実装 | 複数残っている。特に upload retry queue と inventory session は本番前に方針決定が必要 | 中 | fake/実機検証中は可 | 一部はい | app 実装依存 |

## 現時点で危険なもの

### 1. upload retry queue の揮発性

送信失敗 payload は in-memory のため、アプリ再起動で消える。これは本番で「読めたが送れなかったデータ」を失う原因になる。

- 危険度: 高
- 本番前必須: はい
- 依存: app 側
- 推奨: Room または file backed queue

### 2. 16 KB alignment の vendor 未確認

現時点の target 実機で動いていても、16 KB page size 端末では未確認である。vendor AAR 内の native library が原因なので、アプリ側だけで根本解決しにくい。

- 危険度: 中
- 本番前必須: はい
- 依存: vendor 側
- 推奨: Unitech に対応状況を確認し、SDK 更新または対象端末条件を明文化する

## まだ先送りでよいもの

| 項目 | 理由 |
| --- | --- |
| runtime permission / Bluetooth snapshot の永続化 | OS が真の情報源なので、永続化より再評価が安全 |
| fake reader state の永続化 | 開発用の疑似状態であり、本番データではない |
| UI projection state の永続化 | Repository state は画面用の投影なので、元データを永続化すべき |
| sample keystore 対策の追加実装 | 現在 app build に入っていない。docs と ignore で誤利用防止すれば十分 |

## 本番前に必ず確認すべきもの

1. Unitech SDK の 16 KB page size 対応状況。
2. 本番候補端末で RP902 connect / inventory / tag read / upload / retry が通ること。
3. release signing key の管理方法と `:app:signingReport` の確認。
4. upload retry queue の永続化。
5. inventory session を途中復元するか、運用上失われてもよいかの判断。
6. reader settings を端末に保存するか、毎回 default でよいかの判断。
7. ログを現場で取り出せる運用があるか。

## 推奨対応順

| 順番 | 対応 | 理由 |
| --- | --- | --- |
| 1 | upload retry queue 永続化 | データ欠落に直結するため |
| 2 | real RP902 実機で tag read と upload failure/retry を通し確認 | 現場運用の中核だから |
| 3 | Unitech へ 16 KB alignment 対応確認 | vendor 依存でリードタイムが読みにくいため |
| 4 | release signing 方針決定 | 配布直前に詰まると危険なため |
| 5 | Settings 永続化 | 現在は default MAC で回避できるが、複数台運用で必要になるため |
| 6 | structured log 永続化または export | 実機トラブル切り分けを楽にするため |

## 関連資料

- `docs/maintenance/16kb_alignment_assessment.md`
- `docs/maintenance/signing_and_keystore_audit.md`
- `docs/maintenance/in_memory_inventory.md`
- `docs/architecture/RP902_Integration.md`
- `docs/architecture/RP902_SDK_CHECKLIST.md`
