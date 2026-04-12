# 10 FAQ

初学者が疑問に思いやすい点を Q&A 形式でまとめます。

## Q. なぜ最初から real RP902 を使わないのですか？

A. 実機、Bluetooth、権限、vendor SDK、firmware の影響を受けるため、開発とテストが難しくなるからです。fake reader を既定にしておくと、実機がなくても画面、重複除去、upload、ログの確認ができます。

## Q. UI から直接 RP902 SDK を呼んではいけないのはなぜですか？

A. 画面が vendor SDK に依存すると、fake reader と real reader の差し替えが難しくなります。また、SDK 例外や callback thread の問題が画面全体へ広がりやすくなります。vendor SDK は `RealRp902Gateway` に閉じ込めます。

## Q. ViewModel は何をしているのですか？

A. Repository の状態を画面に出しやすい形へ変換し、画面で押されたボタンを repository に渡しています。読取や upload の本体は ViewModel では行いません。

## Q. 同じ EPC が何度も読まれたらどうなりますか？

A. `InventorySession` が同じ EPC を 1 件にまとめます。初回時刻、最終読取時刻、読取回数を更新します。

## Q. `ReaderGateway` は何のためにありますか？

A. reader の共通窓口です。Repository は `ReaderGateway` だけを見ればよく、fake なのか real RP902 なのかを意識しません。

## Q. `ConfigurableReaderGateway` はなぜ必要ですか？

A. Settings で fake / real を切り替えたときに、現在使う gateway を差し替えるためです。切替時には前の gateway を止めてから新しい gateway を使います。

## Q. `RealRp902Gateway` はどこが危険ですか？

A. vendor SDK を直接呼ぶ唯一の場所なので、未確認 API、callback thread、null payload、native crash の影響を受けやすいです。変更時は Javadoc/sample で根拠を確認し、ログと例外防御を入れてください。

## Q. `DisplayOutputSuppressingRp902Reader` はなぜあるのですか？

A. 実機で Start 後に vendor の `DisplayOutput` JNI 経路で crash したためです。アプリ側は直接 `setDisplayOutput` を呼んでいませんが、vendor 内部 thread が呼ぶため、adapter 内で抑止しています。

## Q. upload はもう本物ですか？

A. いいえ。現在は `FakeUploadRepository` です。本物サーバー接続時は `UploadRepository` の新しい実装を追加します。

## Q. retry queue はアプリ再起動後も残りますか？

A. 現在は残りません。`InMemoryUploadRetryQueue` なのでメモリ上だけです。長期運用では永続化実装に差し替える必要があります。

## Q. Logs 画面は何を見るためのものですか？

A. 接続、inventory、tag callback、upload、retry などの内部イベントを見るためです。実機で読めない、接続できない、Start 後に callback が来ない場合の切り分けに使います。

## Q. Settings の preflight は何ですか？

A. real RP902 に接続する前に、MAC address、Android 権限、Bluetooth 状態が揃っているかを確認する仕組みです。足りない条件がある場合、vendor SDK に進む前に止めます。

## Q. Bluetooth MAC はどこで使われますか？

A. Settings で入力され、`ReaderSettings` に保存されます。real mode で connect すると、`RealRp902Gateway` が `TransportBluetooth` を作るときに使います。

## Q. どのファイルから読めばよいですか？

A. まず資料なら `00_はじめに.md`、コードなら `MainActivity.kt`、`AppContainer.kt`、`Rp902App.kt`、`InventoryViewModel.kt`、`DefaultInventoryRepository.kt` の順がおすすめです。

## Q. どこを変えると一番壊れやすいですか？

A. `RealRp902Gateway.kt`、`DefaultInventoryRepository.kt`、`ConfigurableReaderGateway.kt` です。reader 接続、状態管理、callback、upload、ログが絡むため、変更時は fake mode と unit test を必ず確認してください。

## Q. vendor sample source はアプリに入っていますか？

A. いいえ。参照用として vendor 配下に保管していますが、app の dependency path には入れていません。アプリが使うのは `vendor/unitech/runtime` の AAR/JAR です。

## Q. 今後、本物運用に向けて何が必要ですか？

A. real RP902 の実機検証、backend API 確定、retry queue の永続化、設定保存の永続化、ログ保持/出力方針、vendor SDK license/更新運用、16 KB page-size alignment リスク対応が必要です。
