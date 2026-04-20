# 10 FAQ

## Q. なぜ最初から real RP902 を使わないのですか？

A. 実機、Bluetooth、権限、vendor SDK、firmware の影響を受けるため、開発とテストが難しくなるからです。fake reader を既定にしておくと、実機がなくても画面、重複除去、結果登録、ログの確認ができます。

## Q. UI から直接 RP902 SDK を呼んではいけないのはなぜですか？

A. 画面が vendor SDK に依存すると、fake reader と real reader の差し替えが難しくなります。vendor SDK は `RealRp902Gateway` に閉じ込めます。

## Q. UI から直接 PostgreSQL を触ってはいけないのはなぜですか？

A. 画面が接続方式や SQL に依存すると、状態管理、再実行、テストが壊れやすくなるからです。PostgreSQL へのアクセスは repository / gateway 境界に閉じ込めます。

## Q. 同じ EPC が何度も読まれたらどうなりますか？

A. `InventorySession` が同じ EPC を 1 件にまとめます。初回時刻、最終読取時刻、読取回数を更新します。

## Q. 結果登録はもう本物ですか？

A. いいえ。現在は `FakeReadResultRepository` です。PostgreSQL 実接続時は `ReadResultRepository` の新しい実装を追加します。

## Q. pending write はアプリ再起動後も残りますか？

A. 現在は残りません。`InMemoryPendingWriteQueue` なのでメモリ上だけです。長期運用では永続化実装に差し替える必要があります。

## Q. XCgate は何をしますか？

A. PostgreSQL の final result view を参照します。RFID 読取、端末内判定、結果登録は Android アプリ側の責務です。

## Q. どのファイルから読めばよいですか？

A. まず資料なら `00_はじめに.md`、コードなら `MainActivity.kt`、`AppContainer.kt`、`Rp902App.kt`、`InventoryViewModel.kt`、`DefaultInventoryRepository.kt` の順がおすすめです。

## Q. どこを変えると一番壊れやすいですか？

A. `RealRp902Gateway.kt`、`DefaultInventoryRepository.kt`、`ConfigurableReaderGateway.kt` です。reader 接続、状態管理、callback、結果登録、ログが絡むため、変更時は fake mode と unit test を必ず確認してください。

## Q. 今後、本物運用に向けて何が必要ですか？

A. real RP902 の実機検証、PostgreSQL View / Function 契約確定、pending write 永続化、設定保存の永続化、ログ保持/出力方針、vendor SDK license/更新運用、16 KB page-size alignment リスク対応が必要です。
