# Rhino Chat project guidance

## Boundaries

- Android-first CMP, package `dev.sadeghtahani.rhinochat`, minimum Android API 24.
- `androidApp` owns Android hosting; `shared` owns reusable Kotlin and Compose UI. Keep Java/Android APIs in platform source sets.
- The Android submission has a 3–4 hour implementation budget. Record optional iOS showcase effort separately.
- Use [ROADMAP.md](ROADMAP.md) for architecture/feature decisions and [docs/RESEARCH.md](docs/RESEARCH.md) when dependency or tooling guidance needs context. Read only what the change needs.

## UI and state

- Use `RhinoChatTheme` in the application and previews. Render screens from values and callbacks; keep state collection and dependency wiring at route/host boundaries.
- Keep each screen in one Kotlin file, ordered: stateless screen, stateful route/overload, then private preview functions. Keep previews beside their component; do not create separate preview files/classes. Only add actual state ownership when the feature needs it.
- Extract substantial components by responsibility. Avoid generic base classes, pass-through use cases, or extra modules without an actual consumer.
- Place feature models/data classes in the feature's `data` package, database entities in `data/local/data`, and extracted Compose components in `components`. Promote genuinely reused types/components to `common/data` or `common/components` only when a second feature needs them. Follow the separation of persistence entities and external models demonstrated by Now in Android without copying its module count.
- Use common string resources and deterministic, I/O-free previews. A compiled preview is not proof it rendered; an Android-backed preview is not iOS verification.
- For future chat work, persist outgoing client identity before remote I/O, reuse it on retry, and merge acknowledgements/history into the canonical local rows.
- Keep initial/history errors separate from delivery state. Preserve coroutine cancellation; do not swallow it in broad exception wrappers.
- Reuse one application-owned repository/store/fake instance. Commands remain caller-scoped; never run startup recovery during active sends. A fresh repository must refresh the history head before loading older saved offsets.
- Preserve the supplied fake API unchanged in Android code when it is introduced. The fake's in-memory reset and simulated failures need explicit tests/documentation.

## Checks

- `./scripts/verify`: debug APK, isolated UI-test APK compilation (`:androidApp:assembleUiTestAndroidTest`), common metadata, Android host shared/adapter tests, real Room/repository and shared ViewModel tests through `:shared:jvmTest`, and Android lint. The JVM target exists for host testing, not a desktop app.
- `./scripts/verify-ui <device-serial>`: real Compose instrumentation tests on one selected device. The suite drives MainActivity with the real ViewModel/repository/Room and a controlled transport, targets `dev.sadeghtahani.rhinochat.uitest` with in-memory WorkManager and isolated named databases, and must never clear the normal demo cache or consume its retry jobs. Device tests use `:androidApp:connectedUiTestAndroidTest`. A successful test-APK build is not an executed UI test.
- Room entities and DAOs are internal; expose plain models through the local store/repository. Use the Room 3 artifact/import family and retain exported schema snapshots. Do not enable destructive migration fallback.
- Run a focused Gradle task during a change; run the affected required checks at handoff. Use the wrapper, JDK 21, and the pinned version catalog.
- Chat screen previews live at the bottom of `shared/src/commonMain/kotlin/dev/sadeghtahani/rhinochat/chat/ChatScreen.kt`, component previews beside their functions. The welcome screen remains the optional iOS starter. Rendering instructions are in README.
- Stable Material 3 1.9.0 keeps the newer Expressive theme/motion types internal. Reuse `RhinoChatTheme` and `RhinoMotion`; do not introduce hidden API access or a prerelease dependency to follow a newer Android-only example.
- Record actual results and limits in `docs/VALIDATION.md`. Bootstrap smoke tests do not establish chat correctness.

## Repository hygiene

- Keep `local.properties`, `.idea`, build/cache output, signing material, and `artifacts/` out of commits. Preserve wrapper files and Xcode project/shared metadata.
- Keep commits focused and describe actual completed work. Keep AI disclosure current in README.
- Leave changes unstaged and uncommitted for user review unless the user explicitly requests a commit.
- Do not upgrade AGP solely to clear a notification; check installed IDE and KMP compatibility first.
