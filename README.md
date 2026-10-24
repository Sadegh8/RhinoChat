# Rhino Chat

An Android-first Compose Multiplatform code challenge by Sadegh Tahani.

Current milestone: send/retry and pagination correctness. The shared repository and ViewModel support optimistic sends, persisted retry identity, independent history errors, and overlapping offset pages. The app still displays the foundation welcome screen; the next milestone connects this behavior to the chat UI.

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

This builds the debug APK, compiles common metadata, runs shared/adapter tests on the Android host, runs file-backed database/repository tests on the JVM, and runs Android lint. For a focused check:

```sh
./gradlew :androidApp:assembleDebug
./gradlew :shared:testAndroidHostTest
./gradlew :shared:jvmTest
./gradlew :androidApp:lintDebug
```

`ChatLocalStoreTest` checks real Room/SQLite persistence and transactions. `OfflineFirstChatRepositoryTest` adds delayed sends, failure/retry, lost acknowledgements, cancellation, restart, and overlapping history. Common `ChatViewModelTest` checks draft ownership and independent loading/delivery state on both runners. `AndroidChatRemoteDataSourceTest` exercises the actual supplied fake with deterministic failures and virtual time. `PlatformSmokeTest` checks platform wiring. See [actual results](docs/VALIDATION.md).

## Send, retry, and history

Room is the source of messages. Sending first commits a row with a new UUID and `SENDING`; the ViewModel then clears only the submitted draft and calls the remote API. Acknowledgement merges into the same local key and sets `SENT`. Expected failure leaves `FAILED`; retry claims that row and reuses its persisted UUID/text. Repeated retry taps share one in-flight attempt.

History requests fetch 20 at a time, with one request per conversation. Each page merges by identity and commits its returned cursor in the same transaction. Duplicate-only pages still advance; failures retain the cache and cursor. A fresh repository refreshes from the head before using any saved offset.

The application owns one repository/store/fake API instance; the ViewModel owns request lifetimes. Cancellation propagates and in-flight sends become retryable. A new repository recovers interrupted sending rows before its first command. There is no background sender or process-death draft restoration. The supplied fake remains at its default 20% failure rate; its in-memory server resets across process restarts and can reuse server IDs. Conflicting cached identities are preserved and reported rather than overwritten. Exactly-once delivery across a fake-server reset is outside the server's guarantees.

The APK is produced at `androidApp/build/outputs/apk/debug/androidApp-debug.apk`. Select the `androidApp` run configuration in Android Studio to launch it on an emulator or device.

## First previews

Open `shared/src/commonMain/kotlin/dev/sadeghtahani/rhinochat/welcome/WelcomeScreen.kt` in Android Studio and select Split or Design. The file is ordered: stateless screen, resource-owning route, then private light/dark previews. The welcome route has no business state yet; future stateful screen entry points follow the same placement. Both previews use the same `RhinoChatTheme` and stateless `WelcomeScreen` as the application, with common string resources and no network or dependency injection.

Both light and dark previews have been visually verified in the IDE. The installed Android CLI exporter currently fails to export these common previews, so use the IDE preview panel; see [validation notes](docs/VALIDATION.md). Generated artifacts and logs under `artifacts/` are ignored. Android-backed previews do not verify iOS runtime behavior.

## Pinned toolchain

| Component | Version |
| --- | --- |
| Android Gradle plugin | 9.1.1 |
| Gradle wrapper | 9.5.1, with distribution checksum |
| Kotlin / Compose compiler plugin | 2.4.21 |
| Compose Multiplatform | 1.12.2 |
| Material 3 | 1.9.0, stable |
| Room / KSP | 3.0.3 / 2.3.12 |
| Bundled SQLite | 2.7.1 |
| Coroutines / coroutine tests | 1.11.0 |
| Gradle daemon JDK | 21 |
| Android minimum / compile / target SDK | 24 / 37 / 37 |

AGP is deliberately kept at the working template version. The installed Quail 1 IDE supports AGP through 9.2, while the current KMP compatibility table supports AGP through 9.3.1; upgrading to the latest 9.4.1 is outside that verified combination. An update notification alone does not require a project migration. [Android Studio compatibility](https://developer.android.com/studio/releases), [KMP compatibility](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html).

Material 3 versions are independent of CMP versions. Its stable line replaces the generated template's alpha dependency.

## Git and AI workflow

The repository keeps source, Gradle wrapper, Xcode project files, and shared project guidance under version control. Local SDK paths, IDE state, build/cache output, signing keys, and generated verification artifacts are excluded.

[AGENTS.md](AGENTS.md) contains concise project-specific guidance. Use [validation notes](docs/VALIDATION.md) for checks actually completed and remaining limits.

AI assistance has been used for official-documentation research, architecture planning, Android Studio project creation, foundation setup, persistence/identity, send/retry/pagination and ViewModel implementation, and automated verification. The author remains responsible for reviewing and explaining every change. Feature work and validation should extend this disclosure as they happen.
