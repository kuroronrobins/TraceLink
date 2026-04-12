# 08 FAQ

サーバー担当者が疑問に思いやすいことを Q&A でまとめます。

## Q. 今の `Upload completed` は本当にサーバー送信成功ですか？

A. いいえ。現在は `FakeUploadRepository` が non-empty tags なら success を返しているだけです。本物サーバーへ送信していません。

## Q. EPC の重複はアプリ側で処理済みですか？

A. はい、同一 inventory session 内ではアプリ側で重複除去しています。同じ EPC は 1 件になり、`readCount` が増えます。

## Q. ではサーバー側で重複対策は不要ですか？

A. 不要ではありません。retry により同じ session payload が複数回来る可能性があるため、サーバー側でも `sessionId` unique 制約や `(session, epc)` unique 制約を持つのが安全です。

## Q. セッション単位で保存すべきですか、タグ単位で保存すべきですか？

A. 両方です。親として inventory session、子として session 内 tags を保存する構成が扱いやすいです。

## Q. UUID は誰が採番するべきですか？

A. 現在のアプリは `sessionId` を UUID 文字列として採番します。DB 内部 ID はサーバー側で別に採番してよいです。API 契約上は、アプリの `sessionId` を `app_session_id` として保持する案を推奨します。

## Q. Upload ID は何に使うべきですか？

A. 現在の retry queue は `sessionId` を pending upload の ID として使っています。サーバー側では `sessionId` を冪等性キーとして使う初期案が簡単です。upload attempt を個別に追いたい場合は、サーバー側で `upload_attempts.id` を別採番できます。

## Q. 冪等性キーはどこで持つべきですか？

A. 初期案では request body の `sessionId` を使います。将来、より一般的な API にするなら HTTP header `Idempotency-Key` を追加する案もあります。どちらにするかは要合意です。

## Q. サーバー側が時刻を再付与してよいですか？

A. はい。`serverReceivedAt` はサーバー側で付与するのが安全です。ただし、アプリが送る `firstSeenAtEpochMillis` / `lastSeenAtEpochMillis` / `sentAtEpochMillis` は現場時刻として別に保存することを推奨します。

## Q. `deviceId` は今使えますか？

A. 現在は `"android-local-device"` の仮固定値です。本番運用では端末識別子の採番方法をアプリ担当と合意してください。

## Q. reader Bluetooth MAC を保存すべきですか？

A. 現在 upload payload には含まれていません。監査や機器別解析に必要なら追加候補ですが、個体情報として扱う可能性があるため要合意です。

## Q. オフライン再送は誰が責任を持ちますか？

A. アプリは送信失敗 payload を local retry queue に残す責任を持ちます。ただし現在は in-memory なのでアプリ再起動で消えます。サーバーは再送を受けても二重保存しない責任を持ちます。

## Q. 空 tags の session は来ますか？

A. 現在のアプリは空 tags の場合 upload せず、画面上で failed にします。ただし API 側でも `tags` 1 件以上を validation するのが安全です。

## Q. サーバーが業務判定結果をすぐ返す必要はありますか？

A. 初期実装では必須ではありません。まず保存成功を返す構成が簡単です。業務判定が必要になったら、同期レスポンスに含めるか、後続処理にするかを合意してください。

## Q. 同じ sessionId で中身が違う request が来たら？

A. 自動 merge は危険です。`409 Conflict` として保存せず、ログに残して調査対象にする案を推奨します。

## Q. アプリ側を将来のサーバー変更に強くするには？

A. アプリは `UploadRepository` interface だけに依存し、本物 HTTP 実装を data 層に閉じ込めます。API response も app-owned な `UploadResult` に変換してから repository に返す構成が安全です。

