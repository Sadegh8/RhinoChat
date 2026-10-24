# Foundation validation

Checked on 24 October 2026 in the existing project directory. This record covers the CMP foundation, not the chat feature.

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

The single current test exercises the common test runner and platform actual factory. It is an explicitly identified bootstrap smoke test. It does not prove message state, pagination, persistence, retries, or concurrency; those tests belong to the corresponding implementation milestones.

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

## Remaining scope

- No emulator/device app launch was performed in this milestone.
- The SwiftUI iOS host was not built, linked, launched, or tested in a simulator. Shared Kotlin/Native compilation is a narrower result.
- The original fake API and chat behavior are not introduced yet.
- The repository is local; no remote repository has been created or pushed.
