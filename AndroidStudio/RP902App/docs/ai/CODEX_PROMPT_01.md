# CODEX_PROMPT_01

You are working in an Android project for a production-grade RP902 RFID reader app.

Goal:
Build the first shippable vertical slice of an Android app that connects to RP902, reads RFID tags, removes duplicates in-session, shows live results, and prepares payloads for server upload.

Constraints:
- Kotlin only
- Jetpack Compose only for new UI
- Use ViewModel + Repository + UDF
- Do not access RP902 SDK directly from composables
- Keep business logic testable
- Do not invent vendor SDK APIs
- If vendor SDK details are unclear, create an adapter interface and a fake implementation first
- Prefer incremental PR-sized changes
- Update docs when architecture changes

Tasks:
1. Inspect repository structure.
2. Propose a clean layered structure for this app.
3. Create the initial app navigation skeleton.
4. Create connection state model for RP902.
5. Create RP902 adapter interface and fake implementation.
6. Create inventory screen with live list and duplicate filtering.
7. Create upload payload model and repository contract.
8. Add unit tests for duplicate filtering logic.
9. Add TODO markers only where actual RP902 SDK specifics are required.
10. Summarize remaining work required for real device integration.

Output:
- Code changes
- Brief architecture summary
- Explicit assumptions
- Explicit file list

