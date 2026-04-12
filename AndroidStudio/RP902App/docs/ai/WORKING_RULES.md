# WORKING_RULES

- Kotlin only. Do not add Java unless explicitly requested.
- Use Jetpack Compose for all new UI.
- Use ViewModel + Repository + unidirectional data flow.
- Do not access RP902 SDK directly from composables.
- Isolate RP902-specific code under a dedicated device layer or module.
- Do not invent vendor SDK APIs. Inspect actual vendor files first.
- If vendor SDK details are unclear, create an interface plus fake implementation first.
- Keep business logic testable.
- Add or update tests when behavior changes.
- Do not commit secrets, tokens, or signing keys.
- Prefer incremental PR-sized changes.
- Preserve buildability at every step.
- Assume final RP902 validation must happen on a real Android device.

