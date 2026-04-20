# 08 FAQ

## Q. Android が PostgreSQL に直接接続してよいのですか？

A. 現行方針では直接接続します。ただし table 直叩きではなく、View / Function / 制約で安全に利用します。

## Q. XCgate はどこで関わりますか？

A. PostgreSQL の final result view を参照します。RFID 読取や判定、登録は担当しません。

## Q. Android に判定ルールを持たせると更新が大変では？

A. rule bundle を PostgreSQL から取得する前提にし、アプリ本体の構造と rule data を分けます。判定 service は rule bundle を入力として動きます。

## Q. 登録失敗時はどうしますか？

A. Android の `PendingWriteQueue` に bundle を残し、後で再実行します。本番前に durable storage へ差し替えます。

## Q. 旧方針の資料は残しますか？

A. 本文には混ぜません。必要な履歴は `ArchitectureTransitionAudit.md` の migration note に隔離します。
