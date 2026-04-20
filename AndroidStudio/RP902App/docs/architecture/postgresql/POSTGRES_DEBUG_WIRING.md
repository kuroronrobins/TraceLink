# PostgreSQL Debug Wiring

この文書は Android debug build で PostgreSQL smoke mode を有効化するための一時配線を記録する。
production credential architecture ではない。

## Default Behavior

- `MainActivity` は `AppContainerFactory.create()` から依存を受け取る。
- 通常 build と debug build の既定値は fake data access のまま。
- `DataAccessMode.Postgres` は debug build で明示的に Gradle property を渡した場合だけ有効になる。
- release build では `POSTGRES_SMOKE_ENABLED=false` に固定される。

## Enable Debug PostgreSQL Smoke Mode

local container を使う emulator smoke の例:

```powershell
.\gradlew.bat :app:installDebug `
  "-PtracelinkPostgresSmoke=true" `
  "-PtracelinkPostgresHost=10.0.2.2" `
  "-PtracelinkPostgresPort=55432" `
  "-PtracelinkPostgresDatabase=tracelink_smoke" `
  "-PtracelinkPostgresUsername=tracelink_android_app" `
  "-PtracelinkPostgresPassword=<local-smoke-password>" `
  "-PtracelinkPostgresSslMode=Disable" `
  "-PtracelinkPostgresDeviceId=android-local-device" `
  "-PtracelinkPostgresReaderType=RP902"
```

PowerShell では `-P...=10.0.2.2` のような Gradle property 引数を quote する。
quote しない場合、値が task 名として分解されることがある。

physical device から host PC の local container へ接続する場合は、`10.0.2.2` ではなく host PC の LAN IP または VPN 到達可能な DB host を使う。

## Gradle Properties

| property | default | meaning |
| --- | --- | --- |
| `tracelinkPostgresSmoke` | `false` | debug PostgreSQL smoke mode を有効化する |
| `tracelinkPostgresHost` | `10.0.2.2` | emulator から見た host DB address |
| `tracelinkPostgresPort` | `55432` | local smoke container の exposed port |
| `tracelinkPostgresDatabase` | `tracelink_smoke` | smoke DB name |
| `tracelinkPostgresUsername` | `tracelink_android_app` | least-privilege Android role |
| `tracelinkPostgresPassword` | empty | source code に入れない local smoke password |
| `tracelinkPostgresSslMode` | `Disable` | local smoke 用。production では `VerifyCa` / `VerifyFull` を検討する |
| `tracelinkPostgresDeviceId` | `android-local-device` | seed data に一致する deviceId |
| `tracelinkPostgresReaderType` | `RP902` | registration payload に付与する reader type |

`tracelinkPostgresSmoke=true` のとき `tracelinkPostgresPassword` が空なら起動時に失敗する。
これは無意識に PostgreSQL smoke mode が動くことを避けるためである。

## Code Path

| file | responsibility |
| --- | --- |
| `app/build.gradle.kts` | debug build の `BuildConfig.POSTGRES_SMOKE_*` を Gradle property から生成する |
| `AppContainerFactory.kt` | debug smoke が有効な場合だけ `DataAccessMode.Postgres` で `AppContainer` を作る |
| `MainActivity.kt` | `AppContainerFactory` から container を受け取る。DB 詳細は Activity に置かない |

## Limitations

- credential storage は未実装。
- runtime UI switch は未実装。
- durable pending write は未実装。
- release build で PostgreSQL smoke mode を使う経路は用意していない。
- DB 側 validation が通る前に Android smoke 結果を正式な成功扱いにしない。
