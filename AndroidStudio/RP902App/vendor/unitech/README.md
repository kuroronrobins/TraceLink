# Unitech Vendor Assets

This directory separates the original Unitech SDK drop from the files that the app builds against.

## Layout

- `runtime/`
  - Stable app dependency path.
  - `app/build.gradle.kts` may reference files here.
  - Current required files:
    - `unitechRFID_v1.0.41.aar`
    - `UnitechSDK_1.2.19.jar`
- `docs/`
  - Reference material for implementation and review.
  - Current reference files:
    - `javadoc/`
    - `RFID Android programming guide Issue1.pdf`
- `upstream/`
  - Local-only copy of the original vendor SDK distribution, kept out of Git.
  - Sample apps, APKs, Xamarin assets, Gradle wrapper files, and sample signing files stay isolated here.
  - The app must not reference files in this tree directly.
  - Sample keystore folders and `local.properties` files are ignored by Git even if they exist in a local vendor drop.

## Update Procedure

1. Confirm the new SDK license and redistribution terms before committing files.
2. Place the new vendor distribution under `vendor/unitech/upstream/<sdk-name>/` locally.
3. Copy only the app-required runtime binaries into `vendor/unitech/runtime/`.
4. Copy reference documentation into `vendor/unitech/docs/`.
5. Update `app/build.gradle.kts` only if runtime file names change.
6. Update `docs/architecture/RP902_Integration.md` and `docs/architecture/RP902_SDK_CHECKLIST.md`.
7. Run `:app:testDebugUnitTest`, `:app:assembleDebug`, and `:app:lintDebug`.

## Guardrails

- Do not use sample keystores or sample signing configs.
- Do not commit sample keystores or local machine properties unless a human explicitly approves the license/security tradeoff.
- Do not add sample source, APKs, or Xamarin assets to app dependencies.
- Do not assume vendor SDK behavior from sample code unless verified against Javadoc or real hardware.
- Treat `runtime/` as the only supported local dependency boundary until an internal artifact repository is introduced.
- Lint currently reports a 16 KB native library alignment warning for `arm64-v8a/libJNISTUHFL.so` from `runtime/unitechRFID_v1.0.41.aar`. See `../../docs/maintenance/16kb_alignment_assessment.md` and confirm vendor support before release.
- The sample `UTE_Sample.jks` files under upstream sample projects are not app signing material. See `../../docs/maintenance/signing_and_keystore_audit.md` before changing release signing.
