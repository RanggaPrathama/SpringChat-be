# 004 - Conversations and basic groups

## Decision

Conversations support `DIRECT` and `GROUP`. Conversation IDs, user IDs, and message IDs are UUIDs. `conversation_participants` is the only source of membership; conversations do not store user-pair columns or a derived pair key. This first stage has fixed membership: administration, adding/removing members, leaving groups, and renaming groups are deferred.

## Contract

- `POST /api/v1/conversations` is the only conversation-creation endpoint. It accepts `{ "type": "DIRECT", "memberIds": ["<uuid>"] }` or `{ "type": "GROUP", "name": "Study group", "memberIds": ["<uuid>", "<uuid>"] }`. For both types the authenticated user is added automatically. DIRECT requires exactly one other enabled user and no name; an existing conversation returns 200 and a new one returns 201. GROUP requires at least two other distinct enabled users and a nonblank name up to 100 characters; successful creation returns 201. Groups with the same name and members may exist independently.
- Reversed and concurrent direct-chat creation requests resolve to the same conversation. The service transaction asks `UserRepository` to lock the two user rows in database UUID order with PostgreSQL `FOR NO KEY UPDATE`, then asks `ConversationRepository` whether that exact two-member DIRECT already exists. This serializes direct creation while remaining compatible with foreign-key key-share locks used by group participant inserts. Queries and locking live in repositories; the service coordinates the use case.
- Conversation and participant rows are persisted through JPA `save`/`saveAll` within the same service transaction. If any unchecked business or persistence exception escapes, the whole operation rolls back; no explicit commit or rollback calls are made. Entity lifecycle callbacks populate creation/join timestamps when JPA persists these rows.
- The shared backend sanitizer removes HTML markup and active element contents from group names and message text before validation and service logic. Passwords and emails are not HTML-sanitized; message whitespace is preserved where possible. Clients must still render returned text as text rather than raw HTML.
- Conversation detail returns `id`, `type`, `name`, `members`, `otherUser`, and `createdAt`. `members` contains user response objects (`id`, `username`, `email`, `avatarUrl`) for all participants. `name` is null for DIRECT; `otherUser` is null for GROUP. Existing DIRECT `otherUser` fields are preserved and include the same user response fields.
- Conversation summaries include `name` and `memberCount`, retain `otherUser` for DIRECT, and retain the last message, unread count, and activity timestamp. A conversation appears once per page, regardless of its number of members; the pagination total uses the same visibility rules as the page query.
- Only participants can access a conversation, read its messages, send a message, or update their own read marker. Non-members receive the existing not-found response. A DIRECT chat whose other user is disabled remains hidden; an existing GROUP remains visible to its members.
- Messages use the existing REST endpoints and the dedicated idempotency-key contract in spec 005. After a message commits, WebSocket delivery targets every participant, including the sender's subscribed connections. Unread counts exclude the requesting user's own messages; read markers are independent per participant and cannot move backwards or reference another conversation's message.
- The controller retains short `list`, `create`, and `getById` actions and injects the `ChatService` module interface. `ChatServiceImpl` lives beside that interface and implements `listConversations`, `createConversation`, and `getConversationById` together with the message use cases. HTTP DTOs live in `dto/request` and `dto/response`; creation returns the shared internal `CreationResult<ConversationDetailResponse>`. The controller selects 201/200 from `created()` and wraps `data()` in the existing `ApiResponse` JSON. See [backend conventions](../../backend-conventions.md).
- `ConversationQueryRepository` maps summary SQL directly to `ConversationSummaryResponse`, including nested last-message and other-user DTOs, without a projection interface. Its data/count queries retain matching visibility rules and order results by `last_activity_at DESC, id DESC`, using bound limit/offset parameters. A page beyond the final result retains the total; empty conversations have a null last message and zero unread count. When joining an outer write transaction, pending JPA writes are flushed before the JDBC read without committing them.
- `conversations.type` is constrained to DIRECT/GROUP, and only GROUP has a nonblank name. The participants primary key `(conversation_id, user_id)` rejects duplicate membership. The service enforces initial member counts and direct-chat reuse; the database has no pair-based uniqueness constraint.

## Acceptance criteria

- A fresh database is created by the seven per-table migrations documented in `docs/database-migrations.md`, without user-ID conversion, display-name removal, token-hash alteration, or conversation-pair columns.
- Concurrent A-to-B and B-to-A direct-chat requests return the same UUID and exactly two participant rows.
- Creating a group with the creator and two or more other users returns the full member list; invalid names and memberships are rejected.
- A three-member group appears exactly once in each member's conversation list and does not appear in an outsider's list. Its pagination total counts the group once.
- Group messages reach all members through the existing post-commit publication and never reach outsiders. Retried sends do not create another message/event.
- Reading a group message advances only that member's read marker. Another member's unread count is unaffected; an outsider cannot read, send, or mark messages read.
- Creating the same group name and member set again creates a separate GROUP conversation.

## DTO and validation implementation

HTTP DTOs use Lombok @Data classes and explicit camelCase @JsonProperty names. Normalizing setters run before Bean Validation. Field and cross-field constraints protect both @Valid HTTP input and @Validated service calls; database and actor-dependent business rules remain in services. Entity mapping is explicit in module mappers. See [backend conventions](../../backend-conventions.md).

Public errors use actionable Indonesian wording without internal enum names, Java method paths, or database details. Status codes and JSON field names are unchanged; see the user-facing error policy in [backend conventions](../../backend-conventions.md).
