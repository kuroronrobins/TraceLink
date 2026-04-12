# 16 KB alignment warning assessment

最終確認日: 2026-04-12

## 結論

現在の `:app:lintDebug` レポートでは、Unitech SDK の AAR に含まれる native library が `Aligned16KB` warning の対象になっている。現時点で lint が明示している対象は `arm64-v8a/libJNISTUHFL.so` で、出所は `vendor/unitech/runtime/unitechRFID_v1.0.41.aar` である。

これは「今すぐ全端末で実行不能」という意味ではない。一方で、16 KB page size を要求する Android 端末では native library の読み込みや実行に影響する可能性があるため、本番化前に必ず vendor 対応状況を確認する必要がある。

## warning の対象ファイル

lint レポート:

- `AndroidStudio/RP902App/app/build/reports/lint-results-debug.txt`
- issue id: `Aligned16KB`
- message: `The native library arm64-v8a/libJNISTUHFL.so ... is not 16 KB aligned`
- source: `__local_aars__:.../vendor/unitech/runtime/unitechRFID_v1.0.41.aar`

現在の AAR 内に含まれる native library:

| ABI | library | 現在 lint で明示警告あり |
| --- | --- | --- |
| `arm64-v8a` | `libJNISTUHFL.so` | はい |
| `arm64-v8a` | `librfidapi.so` | いいえ |
| `arm64-v8a` | `libSTUHFL.so` | いいえ |
| `armeabi-v7a` | `libJNISTUHFL.so` | いいえ |
| `armeabi-v7a` | `librfidapi.so` | いいえ |
| `armeabi-v7a` | `libSTUHFL.so` | いいえ |
| `x86` | `libJNISTUHFL.so` | いいえ |
| `x86` | `libSTUHFL.so` | いいえ |
| `x86_64` | `libJNISTUHFL.so` | いいえ |
| `x86_64` | `libSTUHFL.so` | いいえ |

注意: lint が現在明示している warning は `arm64-v8a/libJNISTUHFL.so` のみ。ただし SDK 更新や AGP/lint 更新で検出内容が変わる可能性があるため、Unitech AAR 内の native library 全体を確認対象として扱う。

## warning の意味

Android では従来 4 KB page size の端末が一般的だったが、将来または一部端末では 16 KB page size が要求される。native library が 16 KB 境界に対応していない場合、そのような端末で正しく読み込めない、または動作しない可能性がある。

このアプリ自身は NDK コードを持っていない。warning は、依存している Unitech SDK AAR の native library 由来である。

## 現時点の影響評価

| 観点 | 評価 |
| --- | --- |
| 現時点の危険度 | 中 |
| 今すぐ実行不能か | 端末が 4 KB page size なら通常は即時ブロッカーではない。ただし 16 KB page size 端末では未確認。 |
| 放置してよい期間感 | 開発・実機切り分け中は可。ただし本番配布判断前には解消または受容判断が必要。 |
| 本番前必須か | はい。対象端末・Android バージョン・配布条件に照らして必ず確認する。 |
| 依存先 | vendor 依存。アプリ側だけでは native library を安全に再ビルドできない。 |

## app 側でできること

- warning を隠さず、lint レポートに残しておく。
- SDK 更新時に `:app:lintDebug` を再実行し、対象 library が変わっていないか確認する。
- 16 KB page size 端末、または Android の 16 KB page size 対応検証環境で実機確認する。
- vendor に `unitechRFID_v1.0.41.aar` の 16 KB page size 対応版があるか確認する。
- SDK 差し替え時は `vendor/unitech/runtime/` の AAR/JAR のみを更新し、sample source や sample signing material を app dependency に混ぜない。

## app 側でできないこと

- Unitech SDK の `.so` をアプリ側で安全に再ビルドすること。
- vendor の native library が 16 KB page size 端末で動くと断定すること。
- lint warning を suppress して「解決済み」と扱うこと。

## 本番化前の判断ポイント

1. 対象端末が 16 KB page size を要求する可能性があるか。
2. Play 配布や社内配布の基準で `Aligned16KB` warning が許容されるか。
3. Unitech から 16 KB page size 対応 SDK が提供されているか。
4. 対象端末で RP902 connect / inventory / tag read / disconnect が実機確認できているか。
5. warning を受容する場合、端末・OS・SDK version を固定条件として運用資料に残せるか。

## 推奨アクション

| 優先度 | アクション | 担当 |
| --- | --- | --- |
| 高 | Unitech に `unitechRFID_v1.0.41.aar` の 16 KB page size 対応状況を確認する | 人間 |
| 高 | 本番候補端末で `:app:lintDebug` warning と実機動作をセットで確認する | アプリ担当 |
| 中 | SDK 更新時にこの資料と `RP902_SDK_CHECKLIST.md` を更新する | アプリ担当 |
| 低 | warning suppression は、vendor 対応状況と対象端末条件を文書化できた場合のみ検討する | アプリ担当 |
