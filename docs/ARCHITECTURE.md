# Persistence and message identity

Implemented in the shared KMP library. Network orchestration, ViewModels, and the chat UI are subsequent milestones; the app currently displays the foundation welcome screen.

## Packages and boundaries

```text
chat/data/
  ChatMessage.kt                    Plain model consumed by other layers
  DeliveryStatus.kt
  RemoteMessage.kt                  Platform-neutral input for server reconciliation
  MessagePage.kt
  PaginationState.kt
  local/
    ChatDatabase.kt                 Internal Room database, constructor, common builder
    ChatMessageDao.kt               Internal SQL operations and database observations
    ChatLocalStore.kt               Local transactions and mapping to external models
    MessageIdentityConflictException.kt
    data/
      MessageEntity.kt
      ConversationPaginationEntity.kt
```

Android and iOS provide `createChatLocalStore` factories in their platform source sets. Android uses the application's database directory. iOS uses Application Support. Both configure the common database with bundled SQLite. A future application composition root should own/reuse the store and close it when its owner ends; a screen must not open a new database during composition.

`ChatLocalStore` is the local data-source boundary, not a network repository. It exposes plain `ChatMessage` values through `Flow` and keeps Room entities internal. The next repository can combine the supplied API with this store without giving the ViewModel access to a DAO. This follows the entity-to-external-model separation in [Now in Android's offline-first repository](https://github.com/android/nowinandroid/blob/main/core/data/src/main/kotlin/com/google/samples/apps/nowinandroid/core/data/repository/OfflineFirstTopicsRepository.kt).

For screens, keep stateless rendering, the stateful entry point, and previews in the same file in that order. Put feature data classes in `data` and extracted Compose functions in `components`. Promote shared types/components to `common/data` or `common/components` when a second feature needs them; create no empty common packages or speculative abstractions.

## Three identities

| Field | Owner and purpose |
| --- | --- |
| `localId: Long` | SQLite-generated stable row/UI key. It survives acknowledgement and retry. |
| `clientId: String?` | UUID created once when enqueueing an outgoing message and persisted before remote work. The server receives this same value on every attempt. Historical messages can lack it. |
| `serverId: String?` | Server-assigned identity, absent before acknowledgement. Used to merge overlapping history. |

Unique indexes enforce `(conversationId, clientId)` and `(conversationId, serverId)` when the corresponding identity is non-null. SQLite permits multiple nulls, so multiple pending messages and historical messages without client IDs can coexist.

Identical text is not an identity. Two deliberate sends of the same text create two local/client IDs. Message observations sort oldest first by `createdAtMillis`, then `localId`, making equal-time ordering deterministic. Timestamp values are epoch milliseconds supplied by the caller; a future repository owns its clock.

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

The fake loses its server state on process restart and can reuse server IDs. This client detects conflicting identities but cannot preserve server-side idempotency across a server reset. A future repository must surface the conflict and preserve cached messages. Supporting editable server messages or a server-session namespace would require an explicit reconciliation policy beyond the supplied fake's contract.

## Pagination persistence

No pagination row means the initial page has not been saved. An existing row with `nextCursor = null` means a successful fetch reached the end, including an empty conversation. Page data and its cursor are committed together.

The fake cursor is an offset and can shift after inserts. The next repository must serialize history requests and merge overlaps. On a new fake-server session, refresh the head using a null cursor rather than blindly resuming an old persisted offset. Cached metadata is useful for offline state, but does not turn the server's offset into a stable cursor.

## Schema and tests

Room 3.0.3 and KSP generate the database for Android, iOS, and the host JVM. Schema version 1 is exported under `shared/schemas`; the generated implementation stays under ignored build output. Keep the schema snapshot under version control when the user approves a commit. Future released schema changes require a migration; destructive fallback is not enabled.

File-backed database tests live in `shared/src/jvmTest`. A host JVM target ensures Room and SQLite resolve their JVM implementations and native libraries without Android runtime shadows or test-only platform-artifact substitutions. It adds no desktop application or new Gradle module. The Android host runner remains available for Android-specific tests.

Tests cover reopen persistence, stable local/client identity, duplicate history, concurrent retry claims, acknowledgement/history merging, conversation-scoped constraints, interrupted sends, live observations, and page/cursor rollback. See [validation results](VALIDATION.md) for actual runs and remaining boundaries.

References: [Room KMP setup](https://developer.android.com/kotlin/multiplatform/room), [Room 3 stable release and migration differences](https://developer.android.com/jetpack/androidx/releases/room3), [Google's database testing guidance](https://developer.android.com/training/data-storage/room/testing-db), [Now in Android architecture](https://github.com/android/nowinandroid/blob/main/docs/ArchitectureLearningJourney.md).
