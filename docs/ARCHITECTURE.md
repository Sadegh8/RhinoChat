# Chat data and state ownership

Persistence, repository orchestration, the ViewModel, and chat UI are implemented in the shared KMP library. The Android adapter preserves the supplied fake API. Android opens the real chat; the optional iOS starter still requires a remote adapter and host integration.

## Packages and boundaries

```text
chat/data/
  ChatMessage.kt                    Plain model consumed by other layers
  DeliveryStatus.kt
  RemoteMessage.kt                  Platform-neutral input for server reconciliation
  MessagePage.kt
  PaginationState.kt
  ChatUiState.kt                    Screen state, independent history load states
  ChatOperationResult.kt            Completed, ignored, or expected failure
  ChatError.kt                      Shared errors; no Android exceptions in presentation
  repository/
    ChatRepository.kt              Presentation's observation/command boundary
    OfflineFirstChatRepository.kt   Sends, retry claims, history requests and startup recovery
  remote/
    ChatRemoteDataSource.kt         Platform-neutral remote boundary
  local/
    ChatDatabase.kt                 Internal Room database, constructor, common configuration
    ChatMessageDao.kt               Internal SQL operations and database observations
    ChatLocalStore.kt               Local transactions and mapping to external models
    MessageIdentityConflictException.kt
    data/
      MessageEntity.kt
      ConversationPaginationEntity.kt
```

`chat/ChatViewModel.kt` owns draft and operation state. Android's `RhinoChatApplication` starts a single Koin graph, lazily resolving one store, supplied `FakeChatApi()`, restart-safe remote adapter, and repository for the process. `MainActivity` resolves the Activity-owned ViewModel with `koinViewModel()` and passes it into shared `App`. Dependencies remain constructor parameters; common screens/components/previews never access Koin. The graph uses the classic lambda DSL without a compiler plugin. Koin is confined to the Android composition root; iOS wiring remains separate. `ChatRoute` collects with `collectAsStateWithLifecycle` and forwards actions. The adapter and supplied API stay in Android sources; Java APIs stay outside common code.

Android and iOS provide `createChatLocalStore` factories in platform source sets. Android uses the application's database directory; iOS uses Application Support. Each platform calls its own `Room.databaseBuilder`, then applies common bundled-SQLite configuration. The store's owner closes it when that owner ends; a screen must not open a database during composition. Android's store has process lifetime. iOS still needs its own remote adapter and host wiring.

`ChatLocalStore` exposes plain `ChatMessage` values through `Flow` and keeps Room entities internal. `OfflineFirstChatRepository` combines the supplied API with this store without giving the ViewModel access to a DAO. This follows the entity-to-external-model separation in [Now in Android's offline-first repository](https://github.com/android/nowinandroid/blob/main/core/data/src/main/kotlin/com/google/samples/apps/nowinandroid/core/data/repository/OfflineFirstTopicsRepository.kt).

For screens, keep stateless rendering, the stateful entry point, and previews in the same file in that order. Put feature data classes in `data` and extracted Compose functions in `components`. Promote shared types/components to `common/data` or `common/components` when a second feature needs them; create no empty common packages or speculative abstractions.

## Three identities

| Field | Owner and purpose |
| --- | --- |
| `localId: Long` | SQLite-generated stable row/UI key. It survives acknowledgement and retry. |
| `clientId: String?` | UUID created once when enqueueing an outgoing message and persisted before remote work. The server receives this same value on every attempt. Historical messages can lack it. |
| `serverId: String?` | Remote adapter identity, absent before acknowledgement. Used to merge overlapping history. The fake-specific adapter canonicalizes outgoing IDs by client identity; production adapters should use durable backend IDs. |

Unique indexes enforce `(conversationId, clientId)` and `(conversationId, serverId)` when the corresponding identity is non-null. SQLite permits multiple nulls, so multiple pending messages and historical messages without client IDs can coexist.

Identical text is not an identity. Two deliberate sends of the same text create two local/client IDs. Message observations sort oldest first by `createdAtMillis`, then `localId`, making equal-time ordering deterministic. The repository accepts an injectable stable Kotlin `Clock` for outgoing timestamps.

## Persisted operations

- `enqueue`: validates nonblank conversation/text, assigns one client UUID, and inserts a sending row. The insert completes before the caller can begin remote work.
- `savePage`: merges server messages and stores `nextCursor` in one write transaction. The API's cursor advances even if every row was already cached.
- `acknowledge`: verifies the outgoing client identity and merges the server reply into the existing local row.
- `beginRetry`: conditionally changes failed to sending. Concurrent callers can claim the same retry only once. It does not generate identities or make network calls.
- `markFailed`: only changes sending rows, so a late failure cannot regress a confirmed sent row.
- `recoverInterruptedMessages`: changes abandoned sending rows to failed/retryable. Call once at application startup, before starting new sends, never on each route entry. It does not schedule background work.

The database drives `observeMessages` and `observePagination`; there is no parallel mutable message list. Observations map entities to plain values and omit equal repeats. Database reads/writes use Room's coroutine support and default I/O context. Transactions contain local operations only.

## Reconciliation and conflicts

The merge first finds a row by client identity, then server identity. When both refer to different rows, it retains the optimistic row's local ID and removes the redundant history row within the transaction. Repeated pages and acknowledgements update the canonical row rather than inserting another bubble.

The supplied fake treats messages as immutable. Reusing an established identity for different client identity, server identity, text, or sender is reported as `MessageIdentityConflictException`. A page conflict rolls back all rows and the cursor together; no cached message is silently replaced.

The fake loses its server state on process restart and reuses server IDs. Android wraps the unchanged transport adapter with `RestartSafeFakeChatRemoteDataSource`: client-bearing acknowledgements/history use `fake-client:<clientId>`, while the 120 seed messages retain `srv-N`. A mutex-protected preparation step atomically converts legacy acknowledged `srv-*` cache IDs before normalized remote data can merge. This changes only the adapter ID: local/client keys, text, timestamps and delivery status remain intact. Pending rows are untouched, and retry sends the same client ID and text. The conversion needs no schema change or destructive fallback.

This policy is specific to this demo; it does not weaken the common merge checks or reinterpret identity conflicts as network errors. Reused client identity with different text/sender still fails, and pages/cursors still roll back together. Previously sent rows stay cached without replaying them into a fresh fake. The client cannot restore lost server idempotency or promise exactly-once delivery after a fake reset.

## Send lifecycle and state production

The ViewModel exposes immutable `StateFlow<ChatUiState>` from repository observations, its draft, and operation state. Sharing uses `WhileSubscribed(5_000)`; initial loading starts in the ViewModel once, independently of collectors. A rotation must reuse the same ViewModel. A new draft edit increments a version, so even retyping identical text during a pending local write prevents the old send from clearing that new draft.

Local enqueue completes before sending remotely. The short enqueue guard coalesces duplicate taps but releases before the network call, allowing another deliberate message while an earlier send waits. Room updates the delivery status. The ViewModel holds no second mutable message list. Initial-load, older-load, and row delivery failures are independent; a history error retains cached chat.

Repository request sets coalesce work per outgoing row and per conversation. Their mutex covers claims only, never network I/O. Retry also uses the database's conditional failed-to-sending update. A late failure checks the persisted state: if history already confirmed the row, it remains sent.

Suspend commands use caller-owned structured concurrency (`viewModelScope` in presentation). Cancellation is rethrown after a short non-cancellable status/claim cleanup; it is never turned into a network failure. Enqueue cleanup uses the already-created client identity if a commit precedes cancellation of its return, so that row becomes retryable even when its local key never reached the caller. Before the first command, one initialization lock recovers sending rows left by the prior process. Recovery is not repeated while new sends are active. Process termination is handled by startup recovery; no closed-app completion or automatic retry is promised.

The adapter maps `IOException` to a shared network error. The repository separately maps SQLite failures and identity conflicts; unexpected programming errors are allowed to fail visibly. Errors loading history are returned to the ViewModel; network send failures appear on the persisted bubble. Storage/identity send errors also expose an action error.

Database observation has its own retained `ChatCacheState`, upstream of the draft/operation combination. A storage observation failure retains the last readable messages/cursor and exposes a recoverable history error; it cannot complete the final UI flow or freeze input/action-error updates. An explicit initial retry increments an observation attempt and reconnects both local flows. It does not repeat an active initial remote request. Cancellation and unexpected errors are not converted into storage failures, and persistent failures do not trigger an automatic retry loop.

## Pagination persistence

No pagination row means the initial page has not been saved. An existing row with `nextCursor = null` means a successful fetch reached the end, including an empty conversation. Page data and its cursor are committed together.

The fake cursor is an offset and can shift after inserts. The repository allows one history request per conversation, merges overlaps, and uses the server's returned cursor even when a page adds no new rows. Failure or cancellation before saving keeps the previous cursor. An explicit null cursor ends history.

A new repository refreshes the head with a null cursor before permitting older loads; it never blindly resumes a persisted offset. Cached messages remain available if that fetch fails. The baseline has one application-owned repository and one fake-server session. Cached metadata does not turn this offset into a stable cursor; arbitrary server deletions would need a stronger backend contract.

## Compose ownership and motion

`ChatScreen.kt` contains stateless business-state rendering, `ChatRoute`, and private previews in that order. Scroll, focus, interaction, and animation state are local presentation concerns. The screen does not start network loads during composition: a `snapshotFlow` observes the older boundary and the next cursor, independently of active gestures, then dispatches a guarded ViewModel command. Cursor progress retriggers duplicate-only cached pages even if the list does not change or a short Loading/Idle emission is coalesced. The history status retains its maximum measured height while composed, preventing retry from shrinking content at the scroll limit; the header reserves its progress-strip height. Loading indicators reflect actual requests without minimum-duration delays. Failed older loads require explicit retry rather than a repeated automatic loop.

The list reverses Room's oldest-first values for `reverseLayout`, placing newest at the bottom with stable `localId` keys. Older pages extend the far end. A new latest row follows the viewport only if the reader was already at the latest end; browsing history keeps its anchor and exposes an explicit jump control. The lazy state is saveable across activity recreation. Draft/request state remains in the retained ViewModel.

`RhinoChatTheme` supplies shared color, typography, and shapes to production and previews. `RhinoMotion` centralizes Expressive spring values for placement/presses and non-bouncy effects for opacity/color. Stable Material 3 1.9.0 does not publicly expose the newer Expressive theme/motion types; no reflection, hidden API use, or prerelease upgrade is used. Compose's animation machinery handles the platform duration scale; there is no custom animation loop. A disabled-animation device check is separate from this implementation choice.

The Android activity enables edge-to-edge and `adjustResize`. `AdaptiveChatCanvas` consumes safe-drawing insets, including the keyboard, once; its nested Scaffold uses zero native content insets and gives the list its bar padding. `ChatRoute` obtains window posture; pure `calculateChatLayout` translates hinge bounds into local coordinates, selects clear regions, caps wide content, and reduces chrome/input lines for compact height. Separating or occluding vertical hinges keep chat in one clear pane; usable tabletop halves split history and input. Layout changes reuse the same scroll and business-state owners. Platform date/time formatters keep Java/Foundation APIs out of common code.

## Schema and tests

Room 3.0.3 and KSP generate the database for Android, iOS, and the host JVM. Schema version 1 is exported under `shared/schemas`; the generated implementation stays under ignored build output. Keep the schema snapshot under version control when the user approves a commit. Future released schema changes require a migration; destructive fallback is not enabled.

File-backed database tests live in `shared/src/jvmTest`. A host JVM target ensures Room and SQLite resolve their JVM implementations and native libraries without Android runtime shadows or test-only platform-artifact substitutions. It adds no desktop application or new Gradle module. The Android host runner remains available for Android-specific tests.

The JVM test source set also compiles the original Android transport directory, whose sources use JVM APIs rather than Android framework APIs. This lets the actual supplied fake, transport, restart adapter, and real file-backed Room run together without copying their implementations into a test fake. Android remains the production consumer; the JVM target remains a host test target. Common history tests collect the same `historyLoadRequests` snapshot flow as the screen, observing cursor/state/viewport changes without requiring a gesture.

`androidApp/src/androidTest` adds a separate device-level send-flow suite. A custom test Application starts an isolated bootstrap graph so a previously open Activity can restore safely before JUnit begins. The first fixture finishes that old app task and closes the bootstrap graph/database; an outer rule starts a fresh case graph before the actual MainActivity launches. The Activity still resolves its Activity-owned ChatViewModel, backed by the production repository, restart adapter, and Room store. Only transport timing/outcomes are controlled: the unchanged supplied fake commits and deduplicates messages, and the wrapper can hold responses or lose an acknowledgement after commit. Per-test named database files keep this suite separate from the normal demo cache and are deleted after Activity teardown. This tests the real presentation/data path, while production Application graph startup remains a separate integration boundary.

UI actions enter text and tap the actual send/retry controls. Assertions cover both visible field/bubble state and persisted local/client keys, transport attempts, and actual fake-server copies. Delivery labels are scoped to the bubble's stable local-ID test tag; the composer uses its editable-value semantics, distinct from placeholder text. The send control's accessible name stays on the button while its spinner is shown. ActivityScenario recreation holds initial history and send work open to verify ownership without relying on random delay. Tests wait for observable conditions and settle Compose transitions; they contain no fixed sleeps or UI snapshots fed directly into the screen.

Database isolation alone does not prevent Gradle's default post-test APK uninstallation. The project and `verify-ui` explicitly enable `android.injected.androidTest.leaveApksInstalledAfterRun`, preserving the installed demo during test cleanup. The fixture removes only its own named database and Room lock sidecar after Activity teardown. Initial runs without this setting removed the installation and lost recent demo rows; see the validation record rather than treating those runs as cache-preserving.

Tests cover reopen persistence, stable local/client identity, duplicate history, concurrent retry claims, acknowledgement/history merging, conversation-scoped constraints, interrupted sends, live observations, and page/cursor rollback. See [validation results](VALIDATION.md) for actual runs and remaining boundaries.

References: [Room KMP setup](https://developer.android.com/kotlin/multiplatform/room), [Room 3 stable release and migration differences](https://developer.android.com/jetpack/androidx/releases/room3), [Google's database testing guidance](https://developer.android.com/training/data-storage/room/testing-db), [Now in Android architecture](https://github.com/android/nowinandroid/blob/main/docs/ArchitectureLearningJourney.md).
