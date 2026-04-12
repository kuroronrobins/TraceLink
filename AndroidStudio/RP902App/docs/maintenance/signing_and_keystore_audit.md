# Signing and keystore audit

最終確認日: 2026-04-12

## 結論

現在の app build は、Unitech sample keystore を使っていない。

このアプリの `app/build.gradle.kts` には `signingConfigs` が定義されていない。`./gradlew.bat :app:signingReport` の結果では、debug / debugAndroidTest は Android 標準の debug keystore を使い、release は signing config 未設定である。

## 現在の app の署名経路

| Variant | signing config | keystore |
| --- | --- | --- |
| `debug` | Android Gradle Plugin の標準 debug config | `C:\Users\kuroron\.android\debug.keystore` |
| `debugAndroidTest` | Android Gradle Plugin の標準 debug config | `C:\Users\kuroron\.android\debug.keystore` |
| `release` | `null` | `null` |

補足:

- 現在の release build は、リリース用署名をまだ設定していない状態である。
- 本番配布時は、sample keystore ではなく、会社または配布基盤で管理する正式な signing key を使う必要がある。

## sample keystore の所在

ローカルの vendor upstream 配下には、Unitech sample project 由来の keystore が存在する。

| ファイル | 用途 |
| --- | --- |
| `vendor/unitech/upstream/Unitech_RFID_SDK_Android_V1_0_41/1.0.41/Source/AndroidStudio/unitechRFIDSample/keystore/UTE_Sample.jks` | vendor Android sample 用 |
| `vendor/unitech/upstream/Unitech_RFID_SDK_Android_V1_0_41/1.0.41/Source/AndroidStudio/unitechRFIDSample/keystore/keystore.UTE_Sample.PROPERTIES` | vendor Android sample 用 |
| `vendor/unitech/upstream/Unitech_RFID_SDK_Android_V1_0_41/1.0.41/Source/Xamarin/unitechRFID_CSharp_Sample/keystore/UTE_Sample.jks` | vendor Xamarin sample 用 |
| `vendor/unitech/upstream/Unitech_RFID_SDK_Android_V1_0_41/1.0.41/Source/Xamarin/unitechRFID_CSharp_Sample/keystore/keystore.UTE_Sample.PROPERTIES` | vendor Xamarin sample 用 |

これらは `vendor/unitech/upstream/` の中に隔離されており、app の Gradle dependency path には入っていない。

## 使われていない根拠

| 根拠 | 内容 |
| --- | --- |
| app Gradle | `AndroidStudio/RP902App/app/build.gradle.kts` に `signingConfigs` / `storeFile` / `UTE_Sample` の参照がない |
| dependency path | app が参照する vendor file は `vendor/unitech/runtime/unitechRFID_v1.0.41.aar` と `vendor/unitech/runtime/UnitechSDK_1.2.19.jar` のみ |
| settings | `settings.gradle.kts` は `:app` のみを include しており、vendor sample project を build に含めていない |
| signingReport | debug は Android 標準 debug keystore、release は signing 未設定 |
| Git 管理 | `git ls-files AndroidStudio/RP902App/vendor/unitech/upstream` は空。upstream は追跡対象外 |
| ignore rule | root `.gitignore` と app `.gitignore` で upstream / keystore / local properties を app dependency path から切り離す |

## vendor sample 側の署名設定

vendor sample project の `app/build.gradle` には `UTE_Sample` signing config が存在し、debug / release に sample signing config を割り当てている。

これは vendor sample を単独でビルドするための設定であり、本アプリの `:app` module には読み込まれていない。

## 将来誤利用しないための注意点

- `vendor/unitech/upstream/` の sample project を `settings.gradle.kts` に include しない。
- sample keystore を `app/build.gradle.kts` の `signingConfigs` に流用しない。
- 本番 keystore は Git に直接コミットせず、CI secret、社内 key 管理、またはローカル `keystore.properties` などで安全に扱う。
- SDK 更新時に sample source や sample signing material を `vendor/unitech/runtime/` にコピーしない。
- release signing を追加するときは、この資料を更新し、`signingReport` の結果を確認する。

## リリース前に確認すべきこと

| 項目 | 本番前必須 | 担当 |
| --- | --- | --- |
| 正式な release signing key の管理方法 | はい | 人間 / CI 担当 |
| `:app:signingReport` で release keystore が正式 key を指すこと | はい | アプリ担当 |
| sample keystore 参照がないこと | はい | アプリ担当 |
| keystore や password が Git に入っていないこと | はい | アプリ担当 |
| debug keystore のまま配布しないこと | はい | アプリ担当 |

## 現時点のリスク評価

| 観点 | 評価 |
| --- | --- |
| 現時点の危険度 | 低 |
| 放置してよい期間感 | 開発中は可。release signing 設定を始める前に再確認する |
| 本番前必須か | はい。release signing key は本番前に必ず決める |
| 依存先 | app 側の運用・CI 設定依存 |
