# Validation

Checked on 24 October 2026 in the existing project directory. This record covers foundation, persistence/identity, repository send/retry/pagination, shared ViewModel behavior, and Compose integration/previews.

## Verified results

| Check | Result | Evidence |
| --- | --- | --- |
| Initial generated starter | Passed | Android debug build and three generated example tests completed before foundation changes |
| Updated Android debug build | Passed | `:androidApp:assembleDebug`; APK at `androidApp/build/outputs/apk/debug/androidApp-debug.apk` |
| Shared Android host test runner | Passed | `:shared:testAndroidHostTest`; one `PlatformSmokeTest`, zero failures/errors/skips |
| Android lint | Passed with warnings | `:androidApp:lintDebug`; zero errors, six warnings described below |
| Shared Kotlin/Native compilation | Passed | `:shared:compileKotlinIosSimulatorArm64` |
| Common light/dark Compose previews | Rendered and visually inspected | `WelcomeLightPreview` and `WelcomeDarkPreview` visible in Android Studio, showing the common string resources and shared theme |
| Git ignore rules | Checked | Local SDK/IDE state, build directories, and generated artifacts are ignored; wrapper and Xcode project files are tracked |
| Source whitespace | Checked | `git diff --check` |

The build/test/lint command was `./scripts/verify`. Its log is stored locally at `artifacts/verification/foundation.log`. Kotlin/Native compilation was checked separately, with a local log at `artifacts/verification/ios-compile.log`. Generated logs are intentionally excluded from commits.

At the foundation milestone, the single shared test exercised the common test runner and platform actual factory. That bootstrap test alone did not prove message state, pagination, persistence, retries, or concurrency. The persistence checks below now cover local database behavior.

After colocating the stateless screen, route, and previews in `WelcomeScreen.kt`, `:androidApp:assembleDebug` and `:shared:compileKotlinIosSimulatorArm64` passed again. The local log is `artifacts/verification/colocated-previews.log`.

## Environment and dependency decision

- Android Studio Quail 1, 2026.1.1 Patch 2.
- AGP 9.1.1; Gradle 9.5.1; daemon Java 21.
- Kotlin / Compose compiler 2.4.21; CMP 1.12.2; stable Material 3 1.9.0.
- Android minimum SDK 24, compile/target SDK 37.
- Xcode 27.1 installed; the shared iOS simulator Kotlin compilation passed on this machine.

Android Studio's version lookup reports AGP 9.4.1 as latest stable. The installed IDE's compatibility table supports through AGP 9.2, and the current KMP table supports through AGP 9.3.1. We retain the working AGP 9.1.1 rather than upgrading the IDE/build stack solely to clear a recommendation. [Android Studio table](https://developer.android.com/studio/releases), [KMP table](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html).

## Non-blocking lint findings

- Three version advisories: the Gradle wrapper and two AGP plugin aliases have newer releases. Versions remain pinned for compatibility.
- One generated-resource advisory: the starter launcher foreground is in a redundant `drawable-v24` folder with minimum SDK 24.
- Two generated-icon advisories: the template's normal/round adaptive launcher icons do not provide monochrome layers.

These warnings remain visible. They are not suppressed or represented as zero-warning verification. Custom launcher icon work is outside this foundation milestone.

## Preview tooling limitation

The installed Android CLI preview exporter returned `Failed to obtain RenderResult` for the common preview. A fully qualified composable name returned `Failed to find preview element`. Some failed calls exited with status zero, so exit status alone must not be used as evidence of successful preview export.

After refreshing the IDE's files and opening the original preview file, the standard IDE preview panel rendered both variants. The previews now live at the bottom of `WelcomeScreen.kt`, after the stateless screen and its route. The light preview shows dark text on a light surface; the dark preview shows light text on a dark surface, with readable centered title/subtitle and no clipping. Layout bounds shown by the user's existing preview view settings are tooling overlays, not app UI. No successful standalone PNG export is claimed.

## Persistence and identity validation

The final command `./scripts/verify :shared:compileKotlinIosSimulatorArm64` passed after the persistence changes. Its local log is `artifacts/verification/persistence-validation.log`.

| Check | Current result |
| --- | --- |
| Android debug APK | Built successfully |
| JVM integration tests | All 11 `ChatLocalStoreTest` cases passed, with zero failures/errors/skips |
| Common bootstrap test | Passed on JVM and Android host |
| Shared iOS simulator compilation | Passed, including KSP-generated Room implementation |
| Android lint | Zero errors, the same six non-blocking warnings listed above |
| Exported Room schema | Version 1 contains `messages` and `conversation_pagination`, with the intended unique identity indexes |

The database tests use the production common builder, a real bundled SQLite engine, and temporary on-disk databases. They verify persisted history, unsent state and identity after close/reopen; deterministic equal-time ordering; concurrent retry claims; acknowledgement/history reconciliation; duplicate-page cursor advancement; loaded-empty history; conversation-scoped uniqueness; transaction rollback on conflicting reused identities; interrupted-send recovery; and observed writes.

A plain Android host test initially resolved the device-native SQLite artifact, which cannot load its JNI library on macOS. The JVM test target resolves the actual JVM Room/SQLite implementation without Android shadows or platform-artifact substitutions. No test is skipped to bypass that issue. The APK still uses Android dependencies.

Kotlin reports beta `expect`/`actual` class warnings for Room's documented generated-constructor pattern. Those warnings remain visible; no global suppression was introduced. The iOS factory is compiled, not simulator-runtime tested.

The user approved committing this milestone. Platform-specific Room builder creation was corrected after IDE diagnostics found `databaseBuilder` unresolved in common code. Common metadata compilation is now part of the verification script, in addition to platform builds.

## Send/retry and pagination validation

The full command `./scripts/verify :shared:compileKotlinIosSimulatorArm64` passed. Local log: `artifacts/verification/send-validation.log`. It includes common metadata compilation, Android debug APK, both host test runners, lint, and shared iOS simulator Kotlin compilation.

| Suite | Result |
| --- | --- |
| File-backed local store | 11 tests passed |
| File-backed repository with scripted transport | 15 tests passed |
| Shared ViewModel | 7 tests passed on JVM and the same 7 on Android host |
| Actual supplied fake and Android adapter | 3 tests passed |
| Platform smoke | 1 passed on each runner |

Total: 45 test executions (37 distinct cases), zero failures/errors/skips. The shared cases run on both host targets; this count does not represent device UI tests.

Repository tests cover local optimism before a delayed response, failure then coalesced retry, stable client identity after a lost response, acknowledgement/history races, cancellation during enqueue and remote delivery, claim cleanup, one-time interrupted-send recovery, reopen retry, head/older request coalescing, cursor preservation on failure/cancellation, 120 historical messages plus an inserted send without gaps/duplicates, duplicate-only pages, fresh-session head refresh, and fake reset conflicts.

ViewModel tests cover collector-independent initial loading, draft retention on recollection, independent history errors with cached data, rapid send coalescing during local persistence, retaining newer edits even when text is identical, another send while the first awaits network, failed local writes, explicit retry, and persisted delivery failures. Tests use a controlled Main dispatcher and virtual time. A test teardown issue was corrected by draining ViewModel cancellation before resetting Main.

The adapter tests exercise the original fake with rates 0.0 and 1.0 for deterministic cases. The application still constructs `FakeChatApi()` with its supplied default 0.2; the fake's behavior is unchanged. The extra lost-response scenario is a scripted robustness test, not a claim that the supplied fake fails after committing.

Android Studio analysis confirms the original common Room `databaseBuilder` unresolved reference is fixed and the shared ViewModel/repository resolve. Common metadata and all affected platform compilation also pass. Room's documented generated-constructor beta warnings remain visible. Android lint has zero errors and the same six pre-existing warnings.

## Compose integration and preview validation

`./scripts/verify :shared:compileKotlinIosSimulatorArm64` passed for the UI milestone. Local log: `artifacts/verification/compose-validation.log`. The existing suite has 34 JVM and 11 Android-host executions: 45 total, zero failures/errors/skips. These are behavior/database/adapter tests, not automated device UI tests. Android lint still reports zero errors and the same six pre-existing warnings. Common metadata, Android APK, and shared iOS simulator compilation all pass.

Android Studio analysis reports no unresolved references in `ChatScreen`, all five extracted component files, or `RhinoChatTheme`. The frequently-changing scroll-state warning was fixed with `derivedStateOf`; history observation uses `snapshotFlow`.

Seven screen previews rendered and were visually inspected in Android Studio: populated light/dark, cached initial/action error, loading, empty, RTL with 1.4x text, and wide layout. They use fixed message data and no repository/clock/network. Component preview functions compile and stay beside their components; the seven screen renders exercise those same components. IDE selection/layout bounds are tooling overlays. No successful CLI preview export or iOS preview rendering is claimed.

Manual checks used the connected physical Google Pixel 7 Pro, API 37, in its existing dark appearance:

| Interaction | Observed result |
| --- | --- |
| Launch real Android chat | Supplied fake history appears, newest at the bottom, with readable Persian content, timestamps, and delivery labels |
| Focus composer | Multiline field/send control remain entirely above the keyboard |
| Rotate portrait → landscape → portrait | The unsent `Hello Rhino Team` draft survives activity recreation |
| Send retained draft | One persisted row becomes sent, retaining its local/client identity and gaining `srv-120` |
| Scroll toward older history | Automatic fetch saves cursor `40`; 40 canonical rows include the existing send and deduplicated page overlap |
| Send while browsing history | The `109` history viewport remains in place; new send does not force-scroll |
| Tap Latest messages | Returns to both new sent messages at the bottom |
| Reinstall/relaunch with cache | Both sent messages remain visible; no cache purge was performed |
| Status-bar visual check | Fixed an uncovered inset found on device; the updated header provides a solid background behind legible system icons |

Screenshots/layout dumps are local under `artifacts/verification/chat-*.png` and `chat-*-layout.json`; the final visual check is `chat-final.png`. A read-only snapshot of this app's debug database confirmed persisted identity/count/cursor. Rotation was temporarily controlled for the check and restored to its original free mode afterward.

Both observed device sends succeeded with the supplied default 20% failure rate. Failed/retry rendering was inspected in previews; controlled failure/retry correctness is established by the existing ViewModel/repository/adapter tests. A failed-send retry was not observed on the physical device in this pass. No release performance measurement, TalkBack session, or disabled-animation runtime check is claimed.

## Adaptive layout validation and review findings

`./scripts/verify :shared:compileKotlinIosSimulatorArm64` passed after the adaptive extension. Local log: `artifacts/verification/adaptive-validation.log`. There were 48 JVM and 25 Android-host executions: 73 total, 51 distinct cases, zero failures/errors/skips. Fourteen new layout cases run on both targets and cover sizing thresholds, density, compact height, large text, book/tabletop hinges, RTL selection, window-coordinate translation, and keyboard-constrained regions. Android APK, common metadata, shared iOS simulator Kotlin compilation, and lint passed; lint retains zero errors and six existing warnings.

A physical Pixel 7 Pro landscape screenshot was inspected for compact chrome, readable input, and cutout/gesture clearance. The final twelve-preview matrix compiles; not every adaptive preview was visually inspected. Tablet/foldable emulator runtime checks could not proceed because the available disk space was below the emulator userdata requirement. The attempted keyboard screenshot changed orientation and does not establish adaptive landscape keyboard behavior. Rotation was restored to automatic mode. No additional tests were run after the user requested stopping further testing.

Subsequent user review reported that scrolling to the oldest loaded message does not reliably fetch more history, loading feedback flashes, and new sends repeatedly fail with an identity error after earlier successful sends. These were open acceptance issues at that handoff, despite the passing automated checks and earlier successful manual scenarios. The corrective follow-up below addresses them without clearing cached messages or changing the supplied fake.

## Restart/history/Koin follow-up: 24 October 2026

This follow-up starts from `7986d27`. At the user's request, no new tests were added and no test suites, `scripts/verify`, or lint tasks were run. Earlier passing test counts and lint results above are historical evidence, not verification of this change.

Source findings and fixes:

- The unchanged fake recreates 120 seed rows and reuses outgoing `srv-120`, etc., after process restart. Room preserves earlier acknowledgements with those IDs, so the generic conservative identity merge correctly rejects later distinct messages. The Android demo graph now wraps the transport with client-based outgoing identity normalization and an atomic legacy acknowledgement conversion. The common conflict/transaction policy remains strict. Pending text and client IDs are not regenerated; no data reset is used.
- Automatic older loading required an active scroll, which could end before a request completed or a duplicate-only refreshed page advanced. The screen now observes the viewport and next cursor while idle, including when stationary. Failed load state disables automatic requests until explicit retry. Each repository call still fetches one page and atomically saves its returned cursor.
- The lazy status row could leave the viewport while loading, and changing an error/button to a small spinner shortened content at the oldest scroll limit. A fixed-height header progress slot displays actual older-page work, while the history status retains its maximum measured height while composed. No minimum loading duration, extra fake latency, or automatic retry delay was added.
- Koin 4.2.2 is pinned in the version catalog, confined to the Android composition root. One module owns process singleton store/fake/adapter/repository definitions and an Activity-owned ChatViewModel definition. Shared App receives the ViewModel explicitly; common constructors, stateless screens, and previews remain independent of Koin.

Actual checks:

| Check | Result and scope |
| --- | --- |
| Final `:androidApp:assembleDebug :shared:compileCommonMainKotlinMetadata`, JDK 21 | Passed. Local build log: `artifacts/verification/restart-fixes-build.log`. Existing Room expect/actual beta warnings remain; no AGP/toolchain upgrade. |
| Source review | Reviewed cancellation propagation, one-time initialization, adapter normalization before merging, atomic legacy conversion, retained local/client keys, strict content conflicts, cursor progress, and idle-only automatic eligibility. This is not a new automated correctness test. |
| In-place install/launch on Pixel 7 Pro API 37, USB `2B041FDH30092R` | Passed with app data retained. Koin graph resolved and chat opened. The wireless transport was not used. |
| Existing reported messages | `Hello Rhino Team`, `History stays put`, `Test`, and `Hi` remained visible. Both failed `Test` and `Hi` reached Sent through explicit retry; no identity-error banner remained. Some retry attempts did not complete successfully before later success; no deterministic failure schedule was substituted. |
| Fresh process restart and new send | Closed/reopened the process, retaining the four messages. `Restart send check` became Sent despite a separate initial-history failure. Initial history recovered through explicit Try again. This exercises the fake's reset/reused-ID path through visible UI; database identities were not exported or independently inspected. |
| Older history | Scrolling reached the old cached boundary at seed 81; a simulated older-load error remained awaiting explicit retry. Retry exposed seeds 80/79 while the viewport stayed in history around 87–81. Further scrolling reached seed 61 and automatically attempted the next page, which produced another explicit-retry error. This covers duplicate cached pages after head refresh, real older-page progress, and boundary-triggered loading; it is not an exhaustive run through all 120 seeds. |
| Final status-sizing build | Built and installed; all five messages above were visible with no error banner on launch. The sizing adjustment was source/build reviewed; no new screenshot, rendered-preview matrix, or frame-by-frame spinner-duration measurement was made. |
| Hygiene | `git diff --check` passed. Supplied FakeChatApi source is unchanged and the graph still calls `FakeChatApi()` with its default failure rate. Changes were left unstaged and uncommitted for the initial review. |

Automatic approval review rejected exporting the conversation database and saving a device screenshot because those actions could copy sensitive conversation content. Neither export was performed; permitted UI hierarchy inspection was used instead. No cache was cleared. Rotation settings were not changed. No emulator/system image was installed.

The default 20% random failure rate is intentional, so occasional send/history failures and explicit retry remain expected. Stable demo identities do not restore the fake's lost idempotency memory or guarantee exactly-once server delivery across resets. A broader deterministic regression suite, accessibility pass, complete final adaptive preview rendering, tablet/foldable runtime, and iOS host integration were not performed in this follow-up.

This is corrective Android/DI work after the UI milestone, not iOS showcase effort. No iOS work was performed. The original 3–4 hour submission target is a budget; the repository does not contain a complete actual-effort ledger, so this follow-up does not claim that total project effort met it.

## Commit and unresolved-reference check: 25 October 2026

The user authorized committing the reviewed restart/history/Koin changes and requested resolving any outstanding files. Git reports no unmerged entries or conflict markers. Android Studio analysis of all ten changed Kotlin files, including the new restart-safe adapter, reports no unresolved references, errors, or warnings; informational style/spelling hints remain.

With JDK 21, `:androidApp:assembleDebug :shared:compileCommonMainKotlinMetadata :shared:compileKotlinJvm :shared:compileKotlinIosSimulatorArm64` passed. This compiles shared production sources for the Android, JVM, and iOS simulator targets; it does not establish an iOS host build, link, or runtime result. Room's generated expect/actual beta warnings remain. The local log is `artifacts/verification/commit-build.log`. No test suites or lint tasks were run, preserving the user's validation constraint.

`git diff --check` passed. The commit includes the new adapter, source/configuration changes, and documentation; ignored build output, local SDK/IDE files, and verification artifacts are excluded. No push was requested or performed.

## Remaining scope

- The SwiftUI iOS host was not built, linked, launched, or tested in a simulator. Shared Kotlin/Native compilation is a narrower result.
- Android's chat UI is wired; the optional iOS host deliberately remains on the welcome starter until it has a platform remote adapter and host integration.
- The corrective pass below adds deterministic regression coverage and a further device walkthrough. Accessibility, API 24 runtime, remaining adaptive runtime checks, and final delivery/effort disclosure remain separate. Process-death draft restoration and background retry remain optional.
- The repository is local; no remote repository has been created or pushed.

## Challenge and code review: 25 October 2026

Reviewed commit `1017906` against all six pages of the supplied challenge PDF. This was a review, not implementation of the remaining features. No production source or checked-in test was changed. The brief's five required features are present in Android: distinct timestamped messages, newest-first remote pages of 20 rendered newest at the bottom, optimistic delivery states, retry with persisted client identity, and cached history/unsent messages. Initial and older-load errors are separate from delivery failures. The supplied fake remains unchanged at its default failure rate. API 24, Compose/Kotlin, Coroutines/Flow, MVVM, Room, Koin, meaningful unit tests, and incremental Git history are present.

Current verification with JDK 21:

| Check | Result |
| --- | --- |
| `./scripts/verify` | Passed: debug APK, common metadata, Android host tests, JVM tests, Android lint |
| Existing Android host tests | 25 executions; zero failures/errors/skips |
| Existing JVM tests | 48 executions; zero failures/errors/skips |
| Distinct existing cases | 51; 22 common cases execute on both runners, giving 73 executions |
| Lint | Zero errors; six warnings: three version advisories, redundant drawable qualifier, two missing monochrome icon layers |
| Git remote | None configured; private repository delivery and reviewer access remain outstanding |

The verification log is local at `artifacts/verification/review/verify.log`. The earlier no-test constraint recorded above describes the preceding follow-up; the current user requested a fresh completeness/code review, and the existing suite was run for this review.

Confirmed code finding: `ChatViewModel.uiState` catches an observation error after combining the database, draft, and operation flows, emits one error state, then completes. While the screen stays subscribed, subsequent draft edits, error dismissal, and load results cannot update that state. An isolated JVM diagnostic injected a `ChatDataException(STORAGE)` from message observation after an initial successful emission; the displayed draft remained at its previous value and the storage banner remained after editing, dismissal, and another initial-load command. Recovery should keep draft/operation state live and provide a way to restart failed database observation. This finding remains unfixed.

The diagnostic was outside the repository source sets and supplied only through a temporary Gradle init script. It passed by asserting the observed frozen-state defect; it is evidence of the issue, not a passing acceptance test. Its source, init script, and log are retained locally under `artifacts/verification/review/`. No regression test for this behavior is checked in.

Remaining review/handoff gaps:

- The restart adapter's successful ID normalization and atomic legacy-cache conversion have device evidence but no checked-in regression test. The existing generic repository reset test deliberately expects an identity conflict without that adapter. The stationary/cursor-driven Compose history trigger also lacks an automated regression test.
- Final-revision rotation with a request in flight, a complete walk through all 120 seed messages, and an end-to-end cached reopen with a forced history failure were not exercised in this review. Earlier manual draft rotation/restart evidence and file-backed persistence tests remain narrower evidence. The fake does not use device connectivity, so airplane mode alone cannot establish its failure behavior.
- No API 24 runtime, new accessibility session, complete final adaptive preview rendering, tablet/foldable runtime, or iOS host integration was performed. Shared compilation and layout tests do not establish those runtime results.
- The actual effort ledger remains missing. Record baseline, research, corrective work, and optional showcase time honestly; confirm the receipt date for the five-day deadline and disclose an overrun if applicable. No deadline or actual hours are inferred from the PDF date or Git timestamps.
- README explains run/test, identity/delivery, architecture, AI use, and current limitations. Before delivery, add concise deferred-work implementation notes (for example saved-state draft recovery and durable scheduled background retry), actual effort, and a final scope statement. The roadmap has the longer extension discussion.

Background retry, process-death draft restoration, an automated send-flow UI test, and functional iOS are not required Android challenge features. Jump to latest, date separators, dark appearance, and RTL-aware layout are implemented; their remaining runtime/accessibility checks should not be represented as completed.

## Observation recovery and regression fixes: 25 October 2026

The user authorized fixing the review findings and testing on the connected Pixel 7 Pro. The database-observation freeze identified above is now fixed. Local cache observation is retained separately from draft/operation state; a read failure keeps the last readable messages/cursor, exposes a storage history error, and pauses automatic older loading. Typing, enqueue/error state, and dismissal remain live. Try again explicitly reconnects both observations, including during an already active initial load, without issuing a duplicate remote request. Cancellation and unexpected failures still propagate. The history storage message now describes access to saved messages rather than only saving changes.

The existing viewport/cursor `snapshotFlow` was extracted into `historyLoadRequests`, which the screen and behavioral tests both use. Its eligibility and distinctness are unchanged. No new UI framework, scheduler, module, dependency upgrade, database schema, or fake API modification was introduced.

New checked-in regression coverage:

- Five ViewModel cases: first-read failure, later message/pagination observation failure, live cached/draft/action state and successful send after recovery, explicit retry during an active history request, and a pending history load/send surviving UI collector removal and restoration without repeated commands. Some cases combine related assertions.
- Five history observer cases: stationary cursor progress with an unchanged list, error/retry gating, leaving/returning to the boundary, unrelated UI updates, and empty/uninitialized/exhausted history.
- Four file-backed restart-adapter integration cases: legacy ID conversion across reopen plus new sends after fake resets; lost acknowledgement/retry with all history pages and one canonical row; atomic rollback before transport on conflicting conversion; and cached history/unsent identity surviving reopen when the supplied fake is configured to fail every request in the test.

The JVM test target compiles the actual Android transport directory alongside the tests. Those sources use JVM APIs only; no production source was copied, and Android production wiring remains unchanged. Production still constructs `FakeChatApi()` with its default failure rate. Only tests select deterministic 0%/100% rates.

| Final check | Actual result |
| --- | --- |
| Focused ViewModel/history JVM tests | Passed before the full run |
| `./scripts/verify :shared:compileKotlinIosSimulatorArm64`, JDK 21 | Passed: debug APK, common metadata, Android host/JVM suites, lint, shared iOS simulator compilation |
| Android host suite | 35 executions; zero failures/errors/skips |
| JVM suite | 62 executions; zero failures/errors/skips |
| Distinct cases | 65; 32 common cases run on both targets, giving 97 executions |
| Lint | Zero errors; the same six existing warnings |
| Compiler | Existing Room generated expect/actual beta warnings remain |
| Source hygiene | `git diff --check` passed; supplied fake, pinned versions, and schema snapshots unchanged |

Logs are local and ignored under `artifacts/verification/recovery-fixes/`. Shared iOS compilation remains narrower than an iOS host build/link/runtime result.

Device checks used the USB-connected Pixel 7 Pro, serial `2B041FDH30092R`, API 37, with app data retained:

| Interaction | Observed result and limit |
| --- | --- |
| Install and launch | Koin graph resolved and cached chat opened |
| Draft rotation | Entered `Pixel regression draft`; portrait → landscape retained the draft |
| Send and immediate rotation back | The draft cleared and one visible row became Sent. After rotation the saved viewport stayed above that row; Latest messages revealed it. This UI check does not independently count server requests or measure whether the network response was still pending at Activity recreation; the controlled ViewModel case holds both requests open across collector recreation. |
| Process restart | Force-stopped/reopened without clearing data; the sent test row remained |
| New send after fake reset | `Pixel restart check` became Sent; the earlier row remained, with no identity error |
| Older-page error/retry | Two default simulated failures were observed at older boundaries. Both stayed awaiting explicit Try again; retry recovered. At the first retry, message 100 kept its vertical position as 99/98 appeared above it. |
| Complete history traversal | Scrolled through the supplied history to message 0 and the You’re at the beginning status. The integration test independently asserts all 120 seed identities plus one outgoing row, without duplicates. |
| Final build install | Installed the final verified APK in place; both sent test rows and cached history remained available |
| Device restoration | Original `wm user-rotation` state was `lock 0`; restored and confirmed `lock 0`. No app data or system image was cleared. |

Both device sends succeeded with the default random failure rate. Lost acknowledgement, failed-send retry, and forced history failure after file-backed reopen are established by controlled tests, not by a deterministic physical-device failure schedule. No screenshot or conversation database was exported during this pass. No API 24 runtime, TalkBack session, complete adaptive preview matrix, tablet/foldable runtime, or iOS host integration is claimed.

README now includes deferred-work approaches and the author's reported effort: approximately one hour so far, supplied during this follow-up, with no phase breakdown or independently timed final total. Subsequent corrective/validation effort should be included in the final disclosure. No separate optional iOS showcase effort was supplied, and no iOS showcase integration was performed here. Receipt date remains unrecorded; no deadline was invented.

The author explicitly chose to create GitHub later. No remote was configured, repository created, push performed, or reviewer invitation sent. All source, tests, and documentation changes remain unstaged and uncommitted for review.

## Automated send-flow UI suite: 25 October 2026

The user explicitly requested a meaningful automated UI test for the send flow and its edge cases. `androidApp/src/androidTest/.../ChatSendFlowTest.kt` now contains four Compose instrumentation cases. They launch the actual MainActivity, enter text and activate the real send/retry controls, then assert visible editable text and message delivery state. They do not feed fabricated ChatUiState snapshots into the screen or substitute a fake ViewModel/repository.

The fixture uses the production ChatViewModel, OfflineFirstChatRepository, restart adapter, and file-backed Room factory on the device. Its only controlled boundary is transport: the unchanged supplied FakeChatApi runs at a test-only 0% random failure rate, with deferred reply gates and an option to lose a response after the real fake commits. Tests check actual fake-server client-ID copies, persisted local/client identities, and transport attempts alongside UI assertions. Production still calls FakeChatApi() with its default 20% failure rate.

| UI case | Assertions |
| --- | --- |
| Lost acknowledgement, then rapid retry | Persisted Sending bubble appears before response; submitted draft clears; a committed-but-lost response shows Failed/Retry; double-tapping retry starts only one additional attempt with the original client ID/text; the same bubble/key becomes Sent; one local row and one actual fake-server message remain |
| Blank input and two intentional identical sends | Empty/whitespace input disables Send and produces no outgoing row/request; a second intentional same-text send is permitted while the first waits; identities differ; replies can complete out of order; both bubbles/rows reach Sent exactly once |
| Activity recreation during pending work | Typed draft survives recreation; both history and a send are deliberately held open across another recreation; a newer draft is retained; initial history and send are each called once; the original bubble reaches Sent when replies are released |
| History failure while sending | An initial load failure does not block optimistic sending/acknowledgement; explicit history retry fetches a page containing the acknowledged message and merges into the existing bubble; the next draft, local identity, and single server copy are retained |

Synchronization uses condition waits for UI/Room/transport observations and Compose idle handling for transitions; there are no fixed sleeps. EditableText semantics are checked separately from placeholder text. Stable local-ID tags scope delivery assertions to their specific bubble. The send button's accessible name now stays present while its enqueue spinner is shown. The Android store factory accepts an optional database name so each fixture can create and remove only its own UUID-named database and Room lock sidecar.

The custom test Application starts an isolated bootstrap graph before a previously open Activity can restore. The first fixture finishes only this app's old task and closes the bootstrap graph/database, then starts a fresh graph for the real Activity under test. Each subsequent case also gets a fresh graph; teardown closes the Activity before the store. An initial plain-Application approach could crash before JUnit when an old task restored without Koin, so it was replaced. Production Application graph startup remains outside this controlled UI suite. These tests exercise ActivityScenario recreation, not process death or a full physical-rotation/keyboard matrix.

Test setup findings and corrections:

- The first device run failed before app actions because Compose UI testing transitively resolved Espresso 3.5.0, whose reflective InputManager initialization fails on API 37. Explicitly including the catalog's existing stable Espresso 3.7.0 fixed it; no hidden-API bypass, alpha release, SDK downgrade, or production toolchain upgrade was used. The Compose UI test artifact 1.12.1 matches the Android UI version delegated by CMP 1.12.2. v2 test rules avoid the deprecated v1 environment.
- Initial field assertions mixed placeholder and editable semantics, and immediate assertions after recreation preceded lifecycle recollection. The helper now waits for the exact editable value; bubble, identity, attempt-count, and server-copy assertions remain intact.
- A negative control temporarily changed submitted-draft clearing to retain the submitted text. The lost-reply UI case failed at its empty-draft condition, as expected. The source was automatically restored and the complete suite passed afterward. The failure is test-quality evidence, not an unresolved application failure.
- Initial setup left Gradle's default APK-uninstall cleanup enabled. This was a setup mistake: after those runs the target package was no longer installed; reinstall showed five earlier cached sent messages, while the two recent Pixel test messages from the preceding manual pass were absent. Their recovery is not claimed. Database isolation inside the fixture did not prevent this runner-level cleanup.
- APK retention is now enabled in both gradle.properties and verify-ui. A subsequent complete device run passed, the package remained installed, and reopening normally retained the same current saved conversation. The fixture also removes Room's test-only .lck sidecars, which Context.deleteDatabase alone leaves behind. A filename-only check found only the normal rhino-chat database and its sidecars after the corrected cleanup; no conversation database was exported.

Commands and evidence:

- `./scripts/verify-ui 2B041FDH30092R`, JDK 21, USB Pixel 7 Pro API 37: all four cases passed, zero failures/errors/skips. The complete suite also passed after restoring the negative-control mutation, after enabling installation retention, and with the final test Application handling a previously open normal app. The last already-open-app run completed successfully in 36 seconds (about 30 seconds in the tests), with the normal app explicitly confirmed open before test startup.
- `./scripts/verify :shared:compileKotlinIosSimulatorArm64`: debug APK, UI-test APK compilation, common metadata, 35 Android-host and 62 JVM executions passed; lint retained zero errors and six existing warnings. Shared iOS compilation passed with the existing Room expect/actual warnings; no iOS host runtime is claimed.
- `scripts/verify` now compiles the device-test APK as part of regular checks. That compilation alone does not execute UI tests; `scripts/verify-ui <device-serial>` selects one transport explicitly and executes them. AGENTS and README document the distinction and the retention setting.
- Gradle's current UI HTML report is at `androidApp/build/reports/androidTests/connected/debug/index.html`, with XML under `androidApp/build/outputs/androidTest-results/connected/debug/`. Local logs, the negative-control failure, and a copied passing XML report are retained under ignored `artifacts/verification/ui-send-flow/`.

No commit or push was performed. API 24 runtime, accessibility tooling, background retry, process-death draft recovery, and iOS host integration remain outside this UI-test result.


### Commit handoff — 25 October 2026

The user authorized committing the reviewed changes. The final required verification plus shared iOS simulator compilation passed again. There were no unmerged files or conflict markers; source changes include all four executed Pixel send-flow tests. Earlier statements that work was uncommitted describe the review stage. Repository creation and pushing remain with the author.

Android Studio still reports six unresolved-reference diagnostics for Android platform declarations in ChatUiFixture after Gradle sync. The real Kotlin compiler resolves them, the test APK compiles, and all four device tests previously executed successfully. These IDE diagnostics remain a tooling limitation; no suppression or copied production code was added to hide them.
