# WORKING_RULES

- Kotlin only. Do not add Java unless explicitly requested.
- Use Jetpack Compose for all new UI.
- Use ViewModel + Repository + UDF.
- Do not access RP902 SDK directly from UI layer.
- Put all RP902-specific code under device-rp902 module.
- Never commit secrets.
- Never edit CI files without explaining why.
- Always update tests when behavior changes.
- Open a PR-sized change, not a giant rewrite.
- Prefer real-device assumptions for RP902 integration, not emulator-only assumptions.