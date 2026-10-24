# Chat data and state ownership

Persistence, repository orchestration, and the ViewModel are implemented in the shared KMP library. The Android adapter preserves the supplied fake API. The app currently displays the foundation welcome screen; chat UI wiring is next.

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

`chat/ChatViewModel.kt` owns draft and operation state. Android's `RhinoChatApplication` lazily constructs one repository/store/adapter/`FakeChatApi()` for the application's lifetime. The Android adapter and original API live under `shared/src/androidMain/.../chat/data/remote`; Java dependencies stay outside common code. The future route will obtain this repository through the host and construct its ViewModel with an explicit factory.

Android and iOS provide `createChatLocalStore` factories in platform source sets. Android uses the application's database directory; iOS uses Application Support. Each platform calls its own `Room.databaseBuilder`, then applies common bundled-SQLite configuration. The store's owner closes it when that owner ends; a screen must not open a database during composition. Android's store has process lifetime. iOS still needs its own remote adapter and host wiring.

`ChatLocalStore` exposes plain `ChatMessage` values through `Flow` and keeps Room entities internal. `OfflineFirstChatRepository` combines the supplied API with this store without giving the ViewModel access to a DAO. This follows the entity-to-external-model separation in [Now in Android's offline-first repository](https://github.com/android/nowinandroid/blob/main/core/data/src/main/kotlin/com/google/samples/apps/nowinandroid/core/data/repository/OfflineFirstTopicsRepository.kt).

For screens, keep stateless rendering, the stateful entry point, and previews in the same file in that order. Put feature data classes in `data` and extracted Compose functions in `components`. Promote shared types/components to `common/data` or `common/components` when a second feature needs them; create no empty common packages or speculative abstractions.

## Three identities

| Field | Owner and purpose |
| --- | --- |
| `localId: Long` | SQLite-generated stable row/UI key. It survives acknowledgement and retry. |
| `clientId: String?` | UUID created once when enqueueing an outgoing message and persisted before remote work. The server receives this same value on every attempt. Historical messages can lack it. |
| `serverId: String?` | Server-assigned identity, absent before acknowledgement. Used to merge overlapping history. |

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

The fake loses its server state on process restart and can reuse server IDs. The repository reports `IDENTITY_CONFLICT` and preserves cached messages; it cannot preserve server-side idempotency across a server reset. Supporting editable server messages or a server-session namespace would require an explicit policy beyond this fake's contract.

## Send lifecycle and state production

The ViewModel exposes immutable `StateFlow<ChatUiState>` from repository observations, its draft, and operation state. Sharing uses `WhileSubscribed(5_000)`; initial loading starts in the ViewModel once, independently of collectors. A rotation must reuse the same ViewModel. A new draft edit increments a version, so even retyping identical text during a pending local write prevents the old send from clearing that new draft.

Local enqueue completes before sending remotely. The short enqueue guard coalesces duplicate taps but releases before the network call, allowing another deliberate message while an earlier send waits. Room updates the delivery status. The ViewModel holds no second mutable message list. Initial-load, older-load, and row delivery failures are independent; a history error retains cached chat.

Repository request sets coalesce work per outgoing row and per conversation. Their mutex covers claims only, never network I/O. Retry also uses the database's conditional failed-to-sending update. A late failure checks the persisted state: if history already confirmed the row, it remains sent.

Suspend commands use caller-owned structured concurrency (`viewModelScope` in presentation). Cancellation is rethrown after a short non-cancellable status/claim cleanup; it is never turned into a network failure. Enqueue cleanup uses the already-created client identity if a commit precedes cancellation of its return, so that row becomes retryable even when its local key never reached the caller. Before the first command, one initialization lock recovers sending rows left by the prior process. Recovery is not repeated while new sends are active. Process termination is handled by startup recovery; no closed-app completion or automatic retry is promised.

The adapter maps `IOException` to a shared network error. The repository separately maps SQLite failures and identity conflicts; unexpected programming errors are allowed to fail visibly. Errors loading history are returned to the ViewModel; network send failures appear on the persisted bubble. Storage/identity send errors also expose an action error.

## Pagination persistence

No pagination row means the initial page has not been saved. An existing row with `nextCursor = null` means a successful fetch reached the end, including an empty conversation. Page data and its cursor are committed together.

The fake cursor is an offset and can shift after inserts. The repository allows one history request per conversation, merges overlaps, and uses the server's returned cursor even when a page adds no new rows. Failure or cancellation before saving keeps the previous cursor. An explicit null cursor ends history.

A new repository refreshes the head with a null cursor before permitting older loads; it never blindly resumes a persisted offset. Cached messages remain available if that fetch fails. The baseline has one application-owned repository and one fake-server session. Cached metadata does not turn this offset into a stable cursor; arbitrary server deletions would need a stronger backend contract.

## Schema and tests

Room 3.0.3 and KSP generate the database for Android, iOS, and the host JVM. Schema version 1 is exported under `shared/schemas`; the generated implementation stays under ignored build output. Keep the schema snapshot under version control when the user approves a commit. Future released schema changes require a migration; destructive fallback is not enabled.

File-backed database tests live in `shared/src/jvmTest`. A host JVM target ensures Room and SQLite resolve their JVM implementations and native libraries without Android runtime shadows or test-only platform-artifact substitutions. It adds no desktop application or new Gradle module. The Android host runner remains available for Android-specific tests.

Tests cover reopen persistence, stable local/client identity, duplicate history, concurrent retry claims, acknowledgement/history merging, conversation-scoped constraints, interrupted sends, live observations, and page/cursor rollback. See [validation results](VALIDATION.md) for actual runs and remaining boundaries.

References: [Room KMP setup](https://developer.android.com/kotlin/multiplatform/room), [Room 3 stable release and migration differences](https://developer.android.com/jetpack/androidx/releases/room3), [Google's database testing guidance](https://developer.android.com/training/data-storage/room/testing-db), [Now in Android architecture](https://github.com/android/nowinandroid/blob/main/docs/ArchitectureLearningJourney.md).
