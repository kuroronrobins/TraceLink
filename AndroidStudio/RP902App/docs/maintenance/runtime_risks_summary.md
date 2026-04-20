# Runtime risks summary

最終更新日: 2026-04-20

## 要約

| テーマ | 結論 | 現時点の危険度 | 本番前必須 |
| --- | --- | --- | --- |
| 16 KB alignment warning | Unitech AAR 由来の `arm64-v8a/libJNISTUHFL.so` が `Aligned16KB` warning 対象 | 中 | はい |
| sample keystore | 現在の app build では使っていない | 低 | はい |
| in-memory 実装 | 特に pending write queue と inventory session は本番前に方針決定が必要 | 中 | 一部はい |
| PostgreSQL 直接アクセス | View / Function / 制約の契約未確定 | 高 | はい |

## 現時点で危険なもの

### 1. pending write queue の揮発性

登録失敗 bundle は in-memory のため、アプリ再起動で消える。これは本番で「読めたが PostgreSQL に登録できなかったデータ」を失う原因になる。

- 危険度: 高
- 本番前必須: はい
- 推奨: Room または file backed queue

### 2. PostgreSQL View / Function 契約未確定

Android が直接 PostgreSQL に接続するため、table 直叩きを避ける View / Function / 制約の設計が本番品質に直結する。

- 危険度: 高
- 本番前必須: はい
- 推奨: work context、rule bundle、equipment snapshot、read result registration、final result view の契約を先に固定する

### 3. 16 KB alignment の vendor 未確認

vendor AAR 内の native library が原因なので、アプリ側だけで根本解決しにくい。

- 危険度: 中
- 本番前必須: はい
- 推奨: Unitech に対応状況を確認する

## 本番前に必ず確認すべきもの

1. PostgreSQL View / Function / 制約の契約。
2. pending write queue の永続化。
3. 本番候補端末で RP902 connect / inventory / tag read / result registration / pending write retry が通ること。
4. Unitech SDK の 16 KB page size 対応状況。
5. release signing key の管理方法。
6. reader settings を端末に保存するかの判断。
7. ログを現場で取り出せる運用。

## 推奨対応順

| 順番 | 対応 | 理由 |
| --- | --- | --- |
| 1 | PostgreSQL View / Function 契約確定 | 直接アクセスの安全性を決めるため |
| 2 | pending write queue 永続化 | データ欠落に直結するため |
| 3 | real RP902 実機で tag read と result registration failure/retry を確認 | 現場運用の中核だから |
| 4 | Unitech へ 16 KB alignment 対応確認 | vendor 依存でリードタイムが読みにくいため |
| 5 | release signing 方針決定 | 配布直前に詰まると危険なため |
| 6 | Settings 永続化 | 複数台運用で必要になるため |
| 7 | structured log 永続化または export | 実機トラブル切り分けを楽にするため |

## 関連資料

- `docs/architecture/TraceLink_CurrentArchitecture.md`
- `docs/architecture/PostgreSQLAccessContract.md`
- `docs/maintenance/16kb_alignment_assessment.md`
- `docs/maintenance/signing_and_keystore_audit.md`
- `docs/maintenance/in_memory_inventory.md`
