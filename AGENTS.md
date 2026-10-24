# Rhino Chat project guidance

## Boundaries

- Android-first CMP, package `dev.sadeghtahani.rhinochat`, minimum Android API 24.
- `androidApp` owns Android hosting; `shared` owns reusable Kotlin and Compose UI. Keep Java/Android APIs in platform source sets.
- The Android submission has a 3–4 hour implementation budget. Record optional iOS showcase effort separately.
- Use [ROADMAP.md](ROADMAP.md) for architecture/feature decisions and [docs/RESEARCH.md](docs/RESEARCH.md) when dependency or tooling guidance needs context. Read only what the change needs.

## UI and state

- Use `RhinoChatTheme` in the application and previews. Render screens from values and callbacks; keep state collection and dependency wiring at route/host boundaries.
- Extract substantial components by responsibility. Avoid generic base classes, pass-through use cases, or extra modules without an actual consumer.
- Use common string resources and deterministic, I/O-free previews. A compiled preview is not proof it rendered; an Android-backed preview is not iOS verification.
- For future chat work, persist outgoing client identity before remote I/O, reuse it on retry, and merge acknowledgements/history into the canonical local rows.
- Keep initial/history errors separate from delivery state. Preserve coroutine cancellation; do not swallow it in broad exception wrappers.
- Preserve the supplied fake API unchanged in Android code when it is introduced. The fake's in-memory reset and simulated failures need explicit tests/documentation.

## Checks

- `./scripts/verify`: debug APK, common tests through `:shared:testAndroidHostTest`, Android lint.
- Run a focused Gradle task during a change; run the affected required checks at handoff. Use the wrapper, JDK 21, and the pinned version catalog.
- First shared previews live in `shared/src/commonMain/kotlin/dev/sadeghtahani/rhinochat/welcome/WelcomeScreenPreview.kt`; rendering instructions are in README.
- Record actual results and limits in `docs/VALIDATION.md`. Bootstrap smoke tests do not establish chat correctness.

## Repository hygiene

- Keep `local.properties`, `.idea`, build/cache output, signing material, and `artifacts/` out of commits. Preserve wrapper files and Xcode project/shared metadata.
- Keep commits focused and describe actual completed work. Keep AI disclosure current in README.
- Do not upgrade AGP solely to clear a notification; check installed IDE and KMP compatibility first.
