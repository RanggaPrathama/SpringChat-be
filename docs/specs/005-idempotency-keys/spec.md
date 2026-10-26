# 005 - Dedicated idempotency keys

## Decision

Request deduplication is shared infrastructure backed by PostgreSQL `idempotency_keys`, separate from business resources. The first consumer is message sending. `messages` contains only its own ID, conversation, sender, content, and creation time. Redis is not involved.

## Contract

- Message sending accepts `{ "content": "Hello", "idempotencyKey": "<uuid>" }`. The client generates one UUID for a new send and reuses it for retries. `clientMessageId` remains a deserialization alias for older callers; `idempotencyKey` is the canonical request name.
- Message content passes through the shared backend HTML-to-text sanitizer before validation, storage, publication, and idempotency fingerprinting. Passwords and emails do not pass through HTML sanitization. Clients must still render returned text as text rather than raw HTML.
- Message responses, history, conversation last-message summaries, and WebSocket message events contain `id`, `conversationId`, `senderId`, `content`, and `createdAt`. Request-deduplication fields are not part of the message resource.
- A key is unique by `(user_id, operation, idempotency_key)`. Sending uses operation `chat.send-message.v1`. Different senders or operations may reuse a key independently.
- A SHA-256 request fingerprint includes the destination conversation UUID followed by a newline and the exact message content. Reusing a key for another conversation or different content returns 409, even if the sender is a member of both conversations. Membership and content validation happen before reservation or replay.
- The service reserves a key before inserting a message. Reservation, message insertion, activity update, and completion all share one transaction. The reservation helper requires an existing transaction. Failure rolls back both the key and the message; retry may then acquire the same key normally.
- `MessageService.sendMessage` persists the message through JPA and flushes it before completing the JDBC-backed reservation. Repositories own the message write, conversation activity update, and recipient lookup; the service coordinates them and publishes the event for delivery after commit. See [backend conventions](../../backend-conventions.md).
- Concurrent requests contend on the table's unique constraint. A competing insert waits for the first transaction; if it commits, the loser reads the completed result and replays it. If it rolls back, the competing request may acquire the reservation. There is no separately committed in-progress record in this workflow.
- `resource_id` identifies the completed result and `completed_at` records completion; both are null during reservation and set together before commit. `operation` defines the resource kind, so `resource_id` is a generic UUID rather than a message-specific foreign key. No new message or event is generated for a replay. A new send returns 201; replay returns the original message ID with 200.
- Completed records are retained for the lifetime of this feature; no TTL or automatic cleanup is enabled. Resource deletion or future retention rules must explicitly handle these references before being introduced. Adding idempotency to another endpoint requires a separate operation and a deterministic fingerprint; a global request filter is outside this iteration.
- Development migrations keep one CREATE TABLE per version: V5 creates messages without `client_message_id`, and V7 creates `idempotency_keys`. This rebased development schema requires a fresh database if any previous baseline has already been applied; no live database is reset automatically.

## Acceptance criteria

- Sending and retrying the same request creates one message, one completed key record, and one publication event.
- Concurrent identical requests return the same message ID; exactly one request reports creation.
- Reusing a key with another content or destination returns 409 and creates no additional message.
- The same UUID used by two different senders produces independent messages and key records.
- A transaction that fails after reservation leaves no key or message behind, and the key can be retried successfully.
- Missing/invalid keys are rejected before business writes; the canonical JSON field and legacy alias both work.
- Fresh migrations contain seven tables; `messages` has no `client_message_id` or `idempotency_key` column. Existing history, unread counts, direct chats, and group fan-out keep working.
