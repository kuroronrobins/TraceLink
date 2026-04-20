# CODEX_PROMPT_01

You are working in an Android project for a production-grade RP902 RFID reader app.

Goal:
Build an Android app that connects to RP902, reads RFID tags, removes duplicates in-session, fetches work/rule/master data from PostgreSQL, performs local judgement on Android, and registers read results to PostgreSQL through View / Function boundaries.

Constraints:
- Kotlin only
- Jetpack Compose only for new UI
- Use ViewModel + Repository + UDF
- Do not access RP902 SDK directly from composables
- Do not access PostgreSQL implementation directly from composables
- Keep business logic testable
- Do not invent vendor SDK APIs
- If vendor SDK or DB details are unclear, create an adapter interface and a fake implementation first
- Prefer incremental PR-sized changes
- Update docs when architecture changes

Current architecture rule:
- No dedicated middle-tier application
- Android app + PostgreSQL + XCgate are the runtime elements
- PostgreSQL is accessed through View / Function / constraints, not table assumptions
- XCgate reads final result views only

Tasks:
1. Inspect repository structure.
2. Preserve clean layered structure.
3. Keep connection state model for RP902 isolated.
4. Keep RP902 adapter interface and fake implementation usable.
5. Maintain inventory screen with live list and duplicate filtering.
6. Maintain read result registration bundle and repository contract.
7. Add unit tests for duplicate filtering and registration state behavior.
8. Add TODO markers only where actual RP902 SDK or PostgreSQL specifics are required.
9. Summarize remaining work required for real device and PostgreSQL integration.

Output:
- Code changes
- Brief architecture summary
- Explicit assumptions
- Explicit file list
