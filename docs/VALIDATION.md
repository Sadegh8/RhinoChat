# Validation

Checked on 24 October 2026 in the existing project directory. This record covers the CMP foundation and persistence/identity layer. Network orchestration and chat presentation are subsequent milestones.

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

## Remaining scope

- No emulator/device app launch was performed in this milestone.
- The SwiftUI iOS host was not built, linked, launched, or tested in a simulator. Shared Kotlin/Native compilation is a narrower result.
- The original fake API, repository/network orchestration, and chat UI are not integrated yet. The welcome screen is unchanged; persistence is currently exercised through its real database tests.
- The repository is local; no remote repository has been created or pushed.
