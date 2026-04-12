# AGENTS.md

## Language
- すべての回答、要約、進捗報告、最終報告は日本語で行うこと。
- コード、識別子、ライブラリ名、API名は必要に応じて英語のままでよい。
- コミットメッセージ案、PR説明案も日本語を優先すること。

## Project goal
- このプロジェクトは RP902 を使った Android RFID 読取アプリである。
- 現時点では、本物の RP902 SDK 統合前の骨格実装を優先する。
- 実装は Kotlin + Jetpack Compose + ViewModel + Repository + UDF を基本とする。

## Guardrails
- Kotlin only。明示指示がない限り Java を追加しない。
- UI から vendor SDK を直接呼ばない。
- RP902 固有処理は app 全体へ漏らさず、抽象化層の背後に隔離する。
- vendor SDK API は推測で書かない。不明な場合は interface + fake 実装で進める。
- buildability を常に維持する。
- 仕様変更を伴う場合は docs も更新する。
- 破壊的変更は避け、PRサイズで小さく進める。

## Reporting format
- 毎回の応答で以下を明記すること。
  1. 実施内容
  2. 変更ファイル
  3. 前提・仮定
  4. 未解決事項
  5. 次に推奨する作業

## Current priorities
1. fake reader ベースでのアプリ骨格強化
2. ログ、再送、永続化の土台追加
3. RP902 実SDK統合のための差し替え点整理
4. テスト追加
5. CI を壊さないこと