# Official documentation research

Checked on 24 October 2026. Primary sources only. This is a dated research snapshot, not a claim that the proposed application has been built or validated. Links are evidence; recommendations in the roadmap are our interpretation for a single-screen challenge.

## Android architecture

[Google: architecture recommendations](https://developer.android.com/topic/architecture/recommendations)

Google recommends a defined data layer, repositories, UDF, ViewModels where applicable, lifecycle-aware collection, and state updates for durable outcomes. Its guidance explicitly allows packages rather than separate modules in small applications. A domain layer is useful for complex or reused logic, rather than universally mandatory.

Project decision: MVVM with UDF, a repository boundary, and a stateless screen. Avoid an MVI framework and pass-through use-case classes unless the implementation reveals a concrete need.

[Google: offline-first data layer](https://developer.android.com/topic/architecture/data-layer/offline-first)

The local data source is the canonical source read by higher layers in an offline-first app. Repositories reconcile local and remote data.

Project decision: persist optimistic messages and delivery status, observe the database for rendering, and preserve cached content when remote work fails. The supplied fake's reset behavior requires a documented limitation; local persistence cannot make that in-memory server durable.

[Google: coroutine best practices](https://developer.android.com/kotlin/coroutines/coroutines-best-practices)

The guidance covers injectable dispatchers, main-safe suspend APIs, immutable observable state, structured lifetimes, and preserving cancellation. It specifically warns against consuming `CancellationException`.

Project decision: control timing in tests, keep I/O out of composables, and explicitly recover persisted sending state after interrupted work. Do not silently treat cancellation as an ordinary failed request in a broad exception wrapper.

## Modularization and Navigation 3

[Google: app modularization](https://developer.android.com/topic/modularization)

Google describes both the benefits and costs of modules. It warns that excessive granularity brings build complexity and boilerplate, and that modularization does not always make sense for a small codebase.

Project decision: one shared library plus the Android host is a useful platform boundary. Keep the single chat feature inside that library. Android-only fallback can use one app module with clear packages.

[Google: modularize Navigation 3 code](https://developer.android.com/guide/navigation/navigation-3/modularize)

The navigation guide does describe feature `api` modules containing navigation keys, with `impl` modules containing entries and navigable content. Its context is separating navigation across features.

Project decision: the user's recollection is correct. There is no second destination in the challenge, so neither a navigation framework nor navigation-contract submodules has a present job. Introduce them when additional independent destinations need that boundary.

[JetBrains: Navigation 3 in CMP](https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html)

The current documentation supports Navigation 3 in shared CMP code from CMP 1.10 onward. Non-JVM saved back stacks require an explicit serialization setup rather than assuming Android reflection works everywhere.

Project decision: Navigation 3 is a viable future option for Android/iOS sharing. Do not add it to demonstrate library familiarity in a one-screen app.

## CMP/KMP toolchain and UI

[JetBrains: compatibility and versions](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)

The fetched compatibility page describes CMP 1.12.2. CMP, Kotlin, and Jetpack Compose release independently. Android builds use Google's Compose artifacts. The Compose compiler plugin must match the Kotlin plugin version, and the broader toolchain has its own compatibility constraints.

Project decision: use a tested stable combination from a current template and pin it; verify Gradle, AGP, KSP, IDE, Xcode if used, and API 24 compatibility before feature work. This research has not selected or compiled the exact dependency matrix.

[JetBrains: platform stability](https://kotlinlang.org/docs/multiplatform/supported-platforms.html)

The fetched documentation lists CMP Android and iOS as stable, with web/Wasm UI in beta.

Project decision: CMP is a credible choice for a team already using it. An iOS extension still needs its own integration and runtime evidence. Stable platform support does not prove our particular dependency combination or app works.

[JetBrains: modern Android application/module structure](https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html)

The AGP 9 migration guide separates the Android application entry point from the KMP library and moves the shared Android target to the dedicated Android KMP library plugin.

Project decision: `androidApp` plus `shared` is a justified small layout. Do not copy legacy templates that apply incompatible plugin combinations to one shared application module. Consult exact selected-version guidance rather than assuming every sample plugin declaration is current.

[JetBrains: shared ViewModel](https://kotlinlang.org/docs/multiplatform/compose-viewmodel.html)

AndroidX-style ViewModels can be used in common code. Non-JVM construction cannot rely on reflection; provide an initializer or a suitable factory. The documentation supports different levels of UI/state sharing.

Project decision: share the ViewModel and state ownership, and construct dependencies explicitly from the host. Do not build parallel independent state holders in Swift and Kotlin.

[JetBrains: common UI previews](https://kotlinlang.org/docs/multiplatform/compose-previews.html)

The recommended annotation is `androidx.compose.ui.tooling.preview.Preview`; the older JetBrains annotation is deprecated from CMP 1.10. Common previews require an Android target, and tooling dependencies depend on the Android plugin configuration.

Project decision: verify one real preview during setup, then reuse deterministic stateless fixtures. Do not equate Android-backed previews with verification of native iOS behavior.

## Persistence and release selection

[Google: Room setup for KMP](https://developer.android.com/kotlin/multiplatform/room)

Room supports shared database definitions with platform-specific database construction. The guide recommends bundled SQLite for consistent behavior across targets.

[Google: Room 3 release notes](https://developer.android.com/jetpack/androidx/releases/room3)

The fetched release table lists stable Room 3.0.3 and preview 3.1.0-alpha03. Room 3 uses the `androidx.room3` package/artifact family and KSP, with coroutine APIs and a required SQLite driver. This differs from many familiar Room 2 examples.

Project decision: evaluate the stable Room line supported by the selected CMP toolchain. Do not paste the alpha dependency from a setup guide just because that guide is official. Do not mix Room 2 imports with Room 3 artifacts or transaction APIs. Confirm the exact choice in the foundation phase.

## OpenAI: instructions and reusable workflows

[OpenAI: AGENTS.md](https://learn.chatgpt.com/docs/agent-configuration/agents-md)

Codex discovers repository guidance through `AGENTS.md`, with directory-scoped instructions. The file is suitable for durable repository expectations and commands.

[OpenAI: skills](https://learn.chatgpt.com/docs/build-skills)

Skills use progressive disclosure: names/descriptions are available initially and full instructions load when relevant. Codex supports repository skills under `.agents/skills`. Skill descriptions should have clear scope.

Project decision: keep a concise root guide and make a project skill only for a real repeatable workflow. Portable file formats still require checking each client's discovery locations.

[OpenAI: revisiting skills and prompts](https://developers.openai.com/blog/rethinking-skills-and-prompts-for-gpt-6-astra)

This guidance cautions against bloated skill catalogs, vague triggers, and requiring all documentation to be read for every edit. It favors contextual references and keeping persistent instructions current.

Project decision: route agents to architecture details for data/state changes, and validation details for behavior changes or handoff. Avoid putting a long architecture tutorial into every prompt. These principles help context use, but do not establish a measured token-saving percentage for this project.

## Google: Android agent guidance

[Google: Gemini AGENTS.md support](https://developer.android.com/studio/gemini/agent-files)

Android Studio Gemini supports project `AGENTS.md` files and directory-scoped guidance. `GEMINI.md` takes precedence when both names exist in the same directory. Agent instructions and on-demand skills have different context-loading behavior.

Project decision: use the canonical shared guide rather than divergent copies for each vendor. Confirm support in the actual installed IDE.

[Google: Android Studio skills](https://developer.android.com/studio/gemini/skills)

The fetched product-specific page, updated 25 September 2026, documents `.agents/skills` or `.android-studio/skills` and notes older releases used different directories. Skills are loaded on demand.

[Google: Android skills overview](https://developer.android.com/tools/agents/android-skills)

Google publishes Android skills for focused tasks such as Navigation 3, edge-to-edge, build migrations, and testing/tooling workflows. The fetched overview and product-specific skill page differ in directory guidance; confirm the applicable client behavior rather than treating either path list as universal.

Project decision: use relevant official skills when work calls for them. Do not vendor a full documentation/skill collection into the challenge or assume Android-specific instructions automatically apply to common Kotlin code.

## Anthropic: shared instructions and verification

[Anthropic: project memory and AGENTS.md](https://code.claude.com/docs/en/memory)

Current docs say Claude Code v2.1.277+ can read `AGENTS.md` directly when eligible project/ancestor Claude instruction files do not override that default. An `@AGENTS.md` import in `CLAUDE.md` remains a supported compatibility route. The docs also explain configuration and session exceptions.

Project decision: do not claim Claude always requires a separate copy. Verify the installed client and loaded guidance; add a minimal import only if necessary. Avoid relying on Codex-specific override filenames across tools.

[Anthropic: Claude Code best practices](https://code.claude.com/docs/en/best-practices)

Anthropic recommends concise persistent instructions, on-demand skills, focused context, and executable verification criteria. It suggests checking real outcomes with builds, tests, and visual evidence rather than trusting generated assertions.

Project decision: a short guide, repeatable verification, and accurate results are more useful here than elaborate orchestration. Keep the interview disclosure factual and make every generated change understandable to its author.

## Send/retry and pagination: implementation check, 24 October 2026

Official guidance was read before this milestone. The stable version lookup retained Kotlin 2.4.21, CMP 1.12.2, shared Lifecycle 2.11.0, coroutines/test 1.11.0, Room 3.0.3, SQLite 2.7.1, and KSP 2.3.12. This milestone needs no added library. AGP stays at the compatible 9.1.1 for the installed IDE; see [validation](VALIDATION.md).

| Official source | Application here |
| --- | --- |
| [Google: offline-first data layer](https://developer.android.com/topic/architecture/data-layer/offline-first) | Room remains canonical; enqueue locally before remote delivery and expose local observations. |
| [Google: coroutine best practices](https://developer.android.com/kotlin/coroutines/coroutines-best-practices) | Caller-owned structured work, narrow expected-error handling, cancellation propagation, injected/test-controlled dependencies. No background completion claim. |
| [Google: state production](https://developer.android.com/topic/architecture/ui-layer/state-production) | Combine local streams with draft/operation state; use `stateIn` and `WhileSubscribed(5_000)`. Start the initial command in the ViewModel, not a collector. |
| [Google: testing StateFlow](https://developer.android.com/kotlin/flow/test) | Keep a collector active in tests for subscription-based state. Override Main and finish ViewModel cancellation before resetting it. |
| [JetBrains: shared ViewModel](https://kotlinlang.org/docs/multiplatform/compose-viewmodel.html) | Common ViewModel with explicit dependencies; platform host factory will wire it into Compose. |
| [Kotlin: stable Clock](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.time/-clock/) | Inject `kotlin.time.Clock` for deterministic outgoing timestamps. |
| [Coroutines: Mutex](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.sync/-mutex/) | Short synchronized request claims; never hold a database transaction or request mutex across remote work. |

Manual pagination fits this single 20-message cursor endpoint and keeps its overlap/identity rules explicit. Paging 3 is optional in the brief; introducing it is unnecessary for this milestone. Tests establish cursor advancement from the returned page, including duplicate-only pages, rather than inferring completion from unique row counts.

## Compose integration and Material Expressive: 24 October 2026

Read before UI implementation: [Google's Material 3 guide](https://developer.android.com/develop/ui/compose/designsystems/material3), [Material's motion physics guidance](https://m3.material.io/styles/motion), [Compose spring customization](https://developer.android.com/develop/ui/compose/animation/customize), [lazy-list keys/animations](https://developer.android.com/develop/ui/compose/lists), [inset consumption](https://developer.android.com/develop/ui/compose/system/insets-ui), and [accessible component defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults).

The IDE version lookup reports CMP Material 3 1.9.0 stable and 1.13.0-beta01 preview. [JetBrains' release notes](https://kotlinlang.org/docs/multiplatform/whats-new-compose-190.html) identify 1.9.0 as based on Jetpack Material 3 1.4.0. Inspecting the resolved 1.9.0 sources confirms `MaterialExpressiveTheme`, `MotionScheme`, and the theme's motion overload are internal. Newer Android documentation for a public API is not proof that this stable CMP artifact exposes it.

Decision: retain the stable library and implement Expressive styling through shared tonal colors, emphasized typography, rounded shapes, and stable Compose animation APIs. `RhinoMotion` uses the pinned source's documented-token values: spatial damping/stiffness 0.8/380, fast spatial 0.6/800, and effects 1.0/1600. Effects do not overshoot opacity or color. Do not claim use of the official `MaterialExpressiveTheme` API. The animation dependency is explicitly declared at the existing CMP version.

Use built-in Material controls and touch targets, meaningful icon descriptions, selectable message text, independent errors, and a multiline input. Avoid decorative online indicators or buttons with no real action. Add spring placement/status transitions without repeated entry animations for old cached content. Keep list observation in `snapshotFlow`/`derivedStateOf` to avoid recomposing the screen on every scroll offset.

The file layout follows the user's preference over the skill's generic separate-route example: stateless screen, route, private previews/fixtures in the same file; extracted Compose components under `components`, data classes under `data`. Native iOS controls/integration remain a separate showcase.

## Adaptive layouts: 24 October 2026

Official guidance read before implementation: [JetBrains adaptive layouts](https://kotlinlang.org/docs/multiplatform/compose-adaptive-layouts.html), [Google window size classes](https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes), and [Google fold awareness](https://developer.android.com/develop/adaptive-apps/guides/foldables/make-your-app-fold-aware). Stable dependency lookup selected CMP adaptive 1.3.0 and Window core 1.5.1.

Use `currentWindowAdaptiveInfoV2` at the route boundary, actual available window space, and separating/occluding hinge geometry in window coordinates. Compact height needs its own treatment, including landscape and keyboard space. A single conversation does not justify inventing a second feature pane: keep book chat clear of the hinge and use upper history/lower input in tabletop when both halves fit. See validation for the distinction between geometry checks, preview compilation, and device runtime evidence.

## Remaining decisions

- Toolchain, previews, common metadata, build, lint, database tests, repository tests, and ViewModel tests now have [recorded evidence](VALIDATION.md).
- The original generic conflict policy remains in the store. The Android demo graph now applies client-based outgoing identity normalization and converts legacy acknowledgement IDs before remote merging; see the follow-up decision below. Earlier generic tests do not establish this new adapter's correctness.
- Keep functional iOS integration separately scoped; decide whether an equivalent iOS fake is acceptable if it will be submitted for assessment.
- The user confirmed a 3–4 hour submission plus an optional, separately tracked showcase. The exact receipt-based deadline remains unconfirmed.
- Validate actual chat UI behavior and activity recreation after Compose integration. Test-level state recollection is narrower evidence than rotating a device.

## Restart/history correction and Koin: 24 October 2026

Read the official [Koin Gradle setup](https://insert-koin.io/docs/setup/gradle/), [release notes](https://insert-koin.io/docs/support/releases/), [Compose ViewModel guide](https://insert-koin.io/docs/reference/koin-compose/compose-viewmodel/), [Android startup guide](https://insert-koin.io/docs/reference/koin-android/start/), and [DSL reference](https://insert-koin.io/docs/reference/koin-core/dsl/). The current stable release is 4.2.2, built with Kotlin 2.3.20; Koin Android requires API 23, below our minimum 24. Its resolved Compose ViewModel Android artifact declares Activity 1.12.4 / Lifecycle 2.9.6, and its Compose artifact declares CMP runtime/foundation 1.10.2. These are below the project's pins (Kotlin 2.4.21, Activity 1.13.0, Lifecycle 2.11.0, CMP 1.12.2). The focused Android build and common metadata compilation validate the actual pinned combination for this follow-up; no AGP upgrade or Koin compiler plugin is introduced.

Decision: keep Koin in `androidApp`, using `koin-android` and the multiplatform `koin-compose-viewmodel` API. One classic-lambda module owns process singletons and a ViewModel definition; `koinViewModel()` uses the Activity's lifecycle owner. Common classes keep constructor injection, shared App accepts the ViewModel explicitly, and deterministic previews call the stateless screen directly. This demonstrates a real DI lifetime boundary, though manual wiring was already adequate; runtime graph lookup and dependency size are the tradeoffs. No iOS graph is started for the welcome-only host.

The Android CLI's official lazy-list guidance ([Google lists](https://developer.android.com/develop/ui/compose/lists)) was read for layout observation and stable keys. The prior automatic trigger required `isScrollInProgress`; it could stop at a stationary boundary. Observe eligibility plus the next cursor so duplicate-only pages still progress after head refresh. Idle-only eligibility prevents automatic error retry loops. Keep real request loading visible outside the lazy status row and preserve its measured space during retry; do not add delays to inflate the spinner.

The supplied fake's source confirms that it resets 120 seed rows and allocates new outgoing `srv-N` by list size. A durable client-based namespace in a demo-specific adapter resolves cross-process ID reuse without editing the fake, clearing Room, or relaxing common content/client conflict checks. A one-time atomic compatibility conversion handles already acknowledged raw IDs. This cannot restore the fake's lost server-side idempotency history.
