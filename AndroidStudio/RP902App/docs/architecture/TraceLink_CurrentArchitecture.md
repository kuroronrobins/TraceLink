# TraceLink 現行アーキテクチャ方針

## 位置づけ

この文書を、TraceLink RP902 App の現行アーキテクチャ判断の基準とする。

旧方針である「Android アプリは読取結果を中央サーバーへ送り、最終判定は中央サーバー側で行う」構成は廃止された。今後は旧方針を前提にした設計、命名、図、TODO を増やさない。

## 新アーキテクチャの要点

- 実行時の主要要素は Android アプリ、PostgreSQL、XCgate の 3 つに絞る。
- 中央サーバー上の専用アプリケーションは置かない。
- Android アプリは PostgreSQL に直接アクセスする。
- Android アプリは作業対象、帳票ルール、設備マスタの必要情報を PostgreSQL から取得する。
- Android アプリは RFID 読取、session 内重複除去、端末内判定、読取結果登録を行う。
- PostgreSQL は table 直叩きの置き場ではなく、Android 用 View / Function / 制約で守られた整合性ガード層とする。
- XCgate は PostgreSQL の最終結果 View を参照する役割に絞る。

## 責務分担

| 要素 | 担当すること | 担当しないこと |
| --- | --- | --- |
| Android アプリ | RP902 接続、RFID 読取、作業対象確定、rule/master 取得、端末内判定、読取結果登録、pending write 管理、診断ログ | PostgreSQL table 構造の直接露出、XCgate 画面保存、中央アプリの代替 |
| PostgreSQL | Android 用 View / Function、帳票ルール、設備マスタ、読取結果、制約、監査情報、XCgate 用 final result view | Android UI、RP902 SDK 呼び出し、端末内セッション状態 |
| XCgate | PostgreSQL の final result view 参照、帳票への結果表示 | RFID 読取、判定ロジック、結果登録、rule/master 整理 |

## Android から PostgreSQL へのアクセス前提

Android は PostgreSQL の table を直接前提にしない。実装では次のような境界を通す。

| Android 側の目的 | PostgreSQL 側の公開境界 |
| --- | --- |
| 作業対象取得 | work context view / function |
| 帳票ルール取得 | rule bundle view / function |
| 設備マスタ取得 | equipment snapshot view / function |
| 読取結果登録 | read result registration function |
| 結果確認 | registration result view |
| XCgate 参照 | final result view |

## Android 内の基本データフロー

1. 作業対象を確定する。
2. PostgreSQL から rule bundle を取得する。
3. PostgreSQL から equipment snapshot を取得する。
4. RP902 で RFID を読み取る。
5. `InventorySession` で session 内重複除去を行う。
6. `ReadJudgementService` で端末内判定を行う。
7. `ReadResultRepository` が PostgreSQL function へ登録する。
8. 登録失敗時は `PendingWriteQueue` に保留し、後で再実行する。
9. XCgate は PostgreSQL の final result view を参照する。

## 命名ルール

旧方針の語彙は本文やコード名へ混ぜない。

| 旧語 | 現行語 |
| --- | --- |
| backend | PostgreSQL / database / postgres gateway |
| central server | PostgreSQL |
| upload | register / write / persist / result registration |
| upload payload | read result registration bundle |
| retry queue | pending write queue |
| final judgement by backend | local judgement on Android |
| API endpoint | PostgreSQL view / function |

legacy 情報が必要な場合は、本文へ混ぜず、`Legacy note` または `Migration note` として隔離する。
