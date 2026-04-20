# 04 PostgreSQL データモデル案

## 論理領域

| 領域 | 内容 |
| --- | --- |
| rule | 帳票ルール、適用条件、version |
| master | 設備マスタ、EPC、設備種別、表示名 |
| result | 読取 session、判定結果、登録結果 |
| api view | Android / XCgate 向け公開 View |
| function | Android から呼ぶ取得・登録 function |
| audit | 登録元端末、操作時刻、失敗理由、再実行履歴 |

## 方針

- Android 向けの公開列は View で絞る。
- 書き込みは Function に集約する。
- 直接更新させたくない整合性は constraint と function 内検証で守る。
- XCgate は final result view を参照するだけにする。
