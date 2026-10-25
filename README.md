# Rhino Chat

An Android-first Compose Multiplatform code challenge by Sadegh Tahani.

Android implements the challenge's single conversation screen with Room-backed cached messages and drafts, optimistic sends, manual retry, persistent WorkManager retry, paginated history, and independent loading errors. Database observation failures preserve the last readable cache and leave input controls live; Try again reconnects observation. The supplied fake remains unchanged; the launch screen keeps its default 20% random failures enabled on a fresh install, with persistent test overrides for reliable or intentionally failing requests. Current build/device evidence and limits are recorded in [validation](docs/VALIDATION.md). The optional iOS host still displays the foundation screen pending its own remote integration.

## Project layout

- `androidApp`: Android application, package `dev.sadeghtahani.rhinochat`, API 24 minimum.
- `shared`: Kotlin Multiplatform library with shared Compose UI and Android/iOS targets. The Android target uses `com.android.kotlin.multiplatform.library`.
- Its JVM target runs real database tests on the development machine; there is no desktop application.
- `iosApp`: generated SwiftUI host for the optional showcase. iOS integration is tracked separately from the required Android submission.

The architecture is MVVM with UDF and a repository backed by local storage. See [architecture](docs/ARCHITECTURE.md) for state ownership, identity, retry, and pagination rules; [the roadmap](ROADMAP.md) for remaining milestones; and [the research notes](docs/RESEARCH.md) for official sources.

## Build and test

Open this directory in Android Studio and let Gradle sync. Use JDK 21 and Android SDK 37 with the existing SDK licenses accepted. Android Studio can create `local.properties` for your machine; it is intentionally ignored by Git.

Use the checked-in Gradle wrapper:

```sh
./scripts/verify
```

This builds the debug APK and instrumented test APK, compiles common metadata, runs shared/adapter tests on the Android host, runs file-backed database/repository tests on the JVM, and runs Android lint. Building the UI-test APK compiles the tests; running them requires a device. For a focused check:

```sh
./gradlew :androidApp:assembleDebug
./gradlew :shared:testAndroidHostTest
./gradlew :shared:jvmTest
./gradlew :androidApp:lintDebug
```

`ChatLocalStoreTest` checks real Room/SQLite persistence and transactions. `OfflineFirstChatRepositoryTest` adds delayed sends, failure/retry, lost acknowledgements, cancellation, restart, and overlapping history. Common `ChatViewModelTest` checks draft ownership and independent loading/delivery state on both runners. `AndroidChatRemoteDataSourceTest` exercises the actual supplied fake with deterministic failures and virtual time. See [actual results](docs/VALIDATION.md).

`ChatViewModelTest` also covers cache observation failures and explicit recovery without freezing drafts or operation state. `ChatHistoryLoadingTest` exercises the actual Compose snapshot observer while stationary, including duplicate-only cursor progress and error/retry gating. `RestartSafeFakeChatRemoteDataSourceTest` combines the actual Android transport/fake sources with file-backed Room on the host JVM to check legacy ID conversion, reset-safe sends, lost responses, rollback, and cached reopen with forced failures. Only test builds share those JVM-compatible transport sources; Android production wiring is unchanged.

Run the automated Compose UI suite on a connected, unlocked device:

```sh
./scripts/verify-ui <device-serial>
```

This selects one transport explicitly, useful when the same phone is connected over both USB and Wi-Fi. Instrumentation targets a separate `dev.sadeghtahani.rhinochat.uitest` app labelled **Rhino Chat Tests**, with in-memory WorkManager and isolated named chat databases. It cannot consume the normal demo app's jobs or replace its cache. APK retention remains enabled. Direct Gradle tasks are `:androidApp:assembleUiTestAndroidTest` and `:androidApp:connectedUiTestAndroidTest`. Results are under `androidApp/build/reports/androidTests/connected/uiTest/` and `androidApp/build/outputs/androidTest-results/connected/uiTest/`.

`ChatSendFlowTest` launches the actual `MainActivity` and uses the real ViewModel, repository, file-backed Room, and supplied fake's idempotency. A test-only transport gates replies and can lose an acknowledgement after a real server commit. UI assertions check Sending/Failed/Retry/Sent, blank input, draft clearing/retention, same-text intentional sends, out-of-order replies, Activity recreation with requests held open, and history failure/recovery. Database keys/client identities, remote attempt counts, and server copies are checked alongside visible bubbles. A custom runner starts a clean Koin graph before the Activity; every test gets a uniquely named database and deletes only that database afterward. The app uses the unchanged fake with failures controlled by the launch screen and retains its normal cache.

## Chat package structure

The chat feature keeps its layers inside `shared`: `chat/domain` contains plain business models, the repository contract and the retry-scheduler contract; `chat/data` contains their implementation, Room/local code and remote adapters/models; `chat/presentation` contains the screen, ViewModel, components, UI state and layout/formatting helpers. Presentation uses domain contracts and data implements them. Android hosts wiring and WorkManager. The Room database package and exported schemas stay stable. There are no additional modules or pass-through use cases. See [architecture](docs/ARCHITECTURE.md).

## Send, retry, and history

Room is the source of messages and per-conversation drafts. Sending atomically commits a row with a new UUID and clears only the matching submitted draft; a draft typed afterward remains saved. The ViewModel immediately attempts delivery. Acknowledgement merges into the same local key and sets `SENT`. Expected failure leaves `FAILED`; retry claims that row and reuses its persisted UUID/text. Repeated retry taps share one in-flight attempt.

Android also enqueues unique one-time WorkManager work for each outgoing row. It requires a connected network, retries network failures with exponential backoff, and survives process death. Before it commits a new outgoing row, the app awaits a unique periodic outbox-repair worker; that sweep covers the crash window between the Room commit and per-message work enqueue even if the app stays closed. App startup also scans pending rows for prompt repair. Automatic retries stop on storage and identity errors; the storage/error state or failed message remains available for explicit handling. Scheduling is subject to Android's background limits; WorkManager may delay execution, and force-stop pauses work until the app is opened again. The persisted demo Go offline setting is applied by the transport, so simulated offline mode is respected after restart and leaves interrupted sends in a retryable failed state.

History requests fetch 20 at a time, with one request per conversation. Each page merges by identity and commits its returned cursor in the same transaction. Duplicate-only pages still advance; failures retain the cache and cursor. A fresh repository refreshes from the head before using any saved offset. If that head connects to already complete cached history, it retains the known beginning instead of replaying cached pages. A gap or acknowledgement-only overlap still requires pagination.

Koin 4.2.2 starts once in `RhinoChatApplication`, with one process-owned store, supplied fake, restart adapter, and repository. `MainActivity` obtains an Activity-owned `ChatViewModel` through `koinViewModel()` and passes it into shared `App`; the ViewModel and repository keep constructor injection. Screens, components, and previews have no Koin lookups. One small graph demonstrates lifetime ownership; manual wiring was also sufficient for this app, and Koin adds a dependency and runtime graph resolution. No compiler plugin or extra module hierarchy is needed.

Foreground and automatic sends share one repository claim per message. Automatic recovery can resume an abandoned `SENDING` row in an already initialized repository without restarting recovery or interrupting other live sends. Storage/identity failures require manual handling. WorkManager is initialized after the production Koin graph; instrumentation replaces it with an in-memory test scheduler. The ViewModel owns foreground request lifetimes. Cancellation propagates and interrupted sending rows become retryable. The supplied fake remains unchanged and its built-in failure rate is set to zero; the surrounding demo transport offers an opt-in 20% failure rate and deterministic faults. It simulates delay/failure rather than actual networking. Its in-memory server resets and reuses `srv-N` after process restart. The Android demo adapter gives outgoing acknowledgements and history a canonical `fake-client:<persisted clientId>` identity; seed messages keep their original IDs. Before the first remote request, it atomically converts earlier acknowledged cache IDs, preserving local/client keys, text, timestamps, and delivery status. Pending rows remain available to WorkManager or explicit retry. Conflicting client/content/sender identities still fail atomically. Cached sent messages are retained locally, not uploaded again. Exactly-once delivery across a fake-server reset remains outside the server's guarantees.

Cache observation is isolated from draft/operation state. A failed read retains the previous messages and cursor, exposes a storage load error, and pauses automatic older loading. Try again reconnects both database observations without duplicating an already active initial request. Persistent storage errors do not cause an automatic retry loop.

The APK is produced at `androidApp/build/outputs/apk/debug/androidApp-debug.apk`. Select the `androidApp` run configuration in Android Studio to launch it on an emulator or device.

## Chat UI and previews

Open `shared/src/commonMain/kotlin/dev/sadeghtahani/rhinochat/chat/presentation/ChatScreen.kt` in Android Studio and select Split or Design. It is ordered: stateless `ChatScreen`, lifecycle-aware `ChatRoute`, then private previews and fixtures. The Android host obtains one lifecycle-owned ViewModel from Koin and passes it to shared `App`. Substantial bubbles, composer, history status, header, and action-error UI live in `chat/presentation/components`; their previews stay beside the component.

The twelve screen previews cover light/dark, cached errors, loading, empty, RTL with larger text, tablet portrait/landscape, phone landscape, a short keyboard viewport, book fold, and tabletop using the real `RhinoChatTheme` and fixed data. The original seven variants were rendered and visually inspected before the adaptive extension; the final twelve-variant set compiles but has not all been visually inspected. The CLI preview exporter remains unreliable, so use the IDE panel; see [validation notes](docs/VALIDATION.md). Preview callbacks are intentionally inert; the application uses real ViewModel callbacks. Android-backed previews do not verify iOS runtime behavior.

The shared design uses lavender tonal containers, contrasting delivery/error states, asymmetric rounded bubbles, selectable text, platform-localized dates/times, and a multiline composer. New rows and delivery labels animate; the send button responds with a small spring/shape change. The latest-message button appears when browsing history. Message keys preserve the scroll anchor across older-page merges; automatic older loads observe the viewport and returned cursor even after a gesture ends. Duplicate-only pages can continue while the oldest boundary remains visible; errors pause until explicit retry. A reserved progress strip remains visible during real older-page work, and the history status retains its measured height during retry to avoid clamping the scroll offset. No artificial loading delay is added. An explicit load/retry button remains available.

Adaptive layout uses available window dimensions and separating/occluding hinge bounds. Wide windows center a bounded conversation; compact height reduces chrome and composer lines. Book posture keeps the conversation clear of the hinge; tabletop places history above and input below when space permits. The adaptive canvas owns safe-drawing/keyboard insets. Physical phone landscape was inspected; tablet/foldable emulator runtime verification is blocked by local disk space.

Stable CMP Material 3 1.9.0 keeps `MaterialExpressiveTheme` and `MotionScheme` internal. This project applies Expressive design and spring-token guidance through stable Material/animation APIs rather than adopting a preview dependency or accessing internals. [Research and compatibility rationale](docs/RESEARCH.md). Google Material Symbols are vendored as six small vectors with their [Apache 2.0 license](docs/licenses/material-symbols.txt).

## Pinned toolchain

| Component | Version |
| --- | --- |
| Android Gradle plugin | 9.1.1 |
| Gradle wrapper | 9.5.1, with distribution checksum |
| Kotlin / Compose compiler plugin | 2.4.21 |
| Compose Multiplatform | 1.12.2 |
| Material 3 | 1.9.0, stable |
| Material 3 adaptive / Window core | 1.3.0 / 1.5.1 |
| Room / KSP | 3.0.3 / 2.3.12 |
| Bundled SQLite | 2.7.1 |
| WorkManager | 2.12.0 |
| Coroutines / coroutine tests | 1.11.0 |
| Koin | 4.2.2 |
| Gradle daemon JDK | 21 |
| Android minimum / compile / target SDK | 24 / 37 / 37 |

AGP is deliberately kept at the working template version. The installed Quail 1 IDE supports AGP through 9.2, while the current KMP compatibility table supports AGP through 9.3.1; upgrading to the latest 9.4.1 is outside that verified combination. An update notification alone does not require a project migration. [Android Studio compatibility](https://developer.android.com/studio/releases), [KMP compatibility](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html).

Material 3 versions are independent of CMP versions. Its stable line replaces the generated template's alpha dependency.

## Git and AI workflow

The repository keeps source, Gradle wrapper, Xcode project files, and shared project guidance under version control. Local SDK paths, IDE state, build/cache output, signing keys, and generated verification artifacts are excluded.

[AGENTS.md](AGENTS.md) contains concise project-specific guidance. Use [validation notes](docs/VALIDATION.md) for checks actually completed and remaining limits.

AI assistance has been used for official-documentation research, architecture planning, Android Studio project creation, foundation setup, persistence/identity, send/retry/pagination and ViewModel implementation, Compose integration, previews, restart/history bug investigation and fixes, official Koin and WorkManager research and integration, device interaction checks, IDE diagnostics, code/challenge review, database-observation recovery, regression tests, instrumented send-flow UI tests, and automated verification. Earlier validation constraints and the later authorized regression/device passes are recorded in [validation](docs/VALIDATION.md). The author remains responsible for reviewing and explaining every change.

## Delivery and deferred work

The author reported approximately one hour spent before this follow-up on 25 October 2026. That is self-reported effort without a phase breakdown or independently measured final total; include this implementation and later validation in the final disclosure. No separate optional iOS showcase effort was reported. The brief allows five days from receipt; the receipt date has not been recorded, so no delivery deadline is inferred from the PDF date. The author will create the private GitHub repository and grant reviewer access later; no remote is configured yet.

- Process-death draft restoration and persistent background retry are implemented with Room-backed per-conversation drafts and unique WorkManager work. The supplied fake still resets its server memory, so exactly-once delivery across that reset is not guaranteed.
- Automated send UI coverage is implemented in `ChatSendFlowTest`; use the separate device command above. Process-death restoration and OS-level WorkManager timing still need runtime checks beyond `ActivityScenario.recreate()`.
- iOS showcase: provide a platform remote adapter, wire the existing shared chat, then build and test the SwiftUI host with keyboard/safe-area/lifecycle checks. It remains outside the required Android submission.
- Additional validation: API 24 runtime, TalkBack, and the complete final tablet/foldable/preview matrix remain to be checked. These are not represented as verified by a successful build.

### Launch test controls

Before chat starts, choose offline mode, failed sends, failed history, an extra 3-second delay, lost first delivery replies, or random failures (20%), then tap **Start app**. Random failures default to on; all other faults default to off. The toggle controls the unchanged supplied fake’s own 20% failure policy, including its normal request delay. Turning it off gives a reliable connection without replacing the server or clearing its messages. Existing saved toggle values are retained. Press Android Back from chat to adjust controls; turn a fault off and retry to recover. Settings persist across process restarts. Network controls never clear saved data. **Reset cache** separately removes downloaded history and its pagination so Start app loads it again; user-created sent, sending, and failed messages remain saved. Reset waits for active history loading to finish. Lost replies commit on the fake server before reporting failure and allow the same client identity to succeed on retry.

Chat shows a persistent **You’re offline** banner while Go offline is enabled. Turning it off removes the banner. This describes the demo control, not physical device connectivity.

The collapsed **Persistence test tools** section adds two safe probes: it can seed a recognizable draft only when the conversation has no saved draft, and it can reschedule failed network sends for about one minute later. To check draft recovery, prepare the sample, start chat, return to test controls, and use **Stop app process**; reopen from the launcher and start chat to see the Room draft. To check a closed-app retry, enable Go offline, send a message, wait for its bubble to show Failed, return to controls, disable Go offline, schedule the retry, and stop the process before the delay ends. Keep the device connected to a network. This stops the process without Android Force stop, which would pause background jobs.
