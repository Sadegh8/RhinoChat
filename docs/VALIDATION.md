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

Subsequent user review reported that scrolling to the oldest loaded message does not reliably fetch more history, loading feedback flashes, and new sends repeatedly fail with an identity error after earlier successful sends. These are open acceptance issues, despite the passing automated checks and the earlier successful manual scenarios. The next milestone must investigate automatic history triggering and the fake server's reused IDs after process restart without clearing cached messages or changing the supplied fake.

## Remaining scope

- The SwiftUI iOS host was not built, linked, launched, or tested in a simulator. Shared Kotlin/Native compilation is a narrower result.
- Android's chat UI is wired; the optional iOS host deliberately remains on the welcome starter until it has a platform remote adapter and host integration.
- Remaining Android acceptance/handoff work includes a broader device failure/retry flow, accessibility checks, and final walkthrough/time disclosure. Process-death draft restoration and background retry remain optional.
- The repository is local; no remote repository has been created or pushed.
