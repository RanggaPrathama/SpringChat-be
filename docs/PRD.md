# Springchat backend product requirements

## Purpose and sources of truth

Springchat provides account-based direct and group chat over a versioned REST API, with WebSocket notification after a message is committed. PostgreSQL owns durable state. Redis currently supports authentication rate limits only. This document explains the end-to-end product flow; the numbered [feature specifications](specs) define the detailed contracts, and [backend conventions](backend-conventions.md) define how to implement them.

## Business flows

### Account and session

1. A visitor registers with a unique username, unique email, and password. The password is stored as a hash. Registration returns the user without opening a session.
2. Login accepts email and password. A successful login creates an independent session, issues a short-lived access JWT, and sets one usable refresh token in an HTTP-only cookie. Invalid credentials have one generic public response.
3. Refresh consumes the current token and issues a replacement for the same session. Reuse of a consumed token revokes the session. Logout revokes that session and consumes its remaining refresh tokens. Existing access JWTs remain valid until expiry.
4. Redis limits login attempts and registrations. If Redis is unavailable, a local limiter keeps auth available with per-instance limits.

See [authentication](specs/001-authentication/spec.md) and [rate limiting](specs/003-redis-rate-limiting/spec.md).

### Users and profiles

- An authenticated user can read and update their own username and avatar, look up another user by ID, and search the username directory. Directory search excludes the requester.
- User responses contain ID, username, email, and avatar URL. Password hashes never leave the backend.

See [user profiles and search](specs/002-user-profile-search/spec.md).

### Conversations and messages

1. An authenticated user creates a DIRECT conversation with one other enabled user or a named GROUP with at least two other enabled users. DIRECT creation reuses the existing conversation for the same pair, including concurrent reversed requests; GROUP creation always creates a new group.
2. Participants can list and inspect their conversations. A non-member cannot inspect a conversation or its messages. A DIRECT conversation with a disabled counterpart is hidden.
3. A participant sends text with an idempotency key scoped to sender, operation, conversation, and content. The first request creates a message; a matching retry returns the same message. Reusing the key for a different request is a conflict.
4. A successful send updates conversation activity. The database transaction also completes the idempotency record. Only after commit is the message delivered to participants over WebSocket. A rolled-back send leaves no message, completed key, or delivery.
5. Participants page through history and advance only their own read marker. A marker cannot move backward or point to a message in another conversation. Unread counts exclude a user's own messages.

See [conversations and groups](specs/004-conversations-and-groups/spec.md) and [idempotency keys](specs/005-idempotency-keys/spec.md).

## API and operational boundaries

The public routes remain under `/api/v1/auth`, `/api/v1/users`, and `/api/v1/conversations`; message routes are nested under a conversation. Controllers accept and validate requests, services execute use cases, repositories own persistence queries, and the global exception handler formats errors. Local schema setup uses Flyway's [development migration baseline](database-migrations.md). Logs go to `logs/springchat.log` in the development profile and use the rules in the backend guide.

Backend callers use three module contracts: `AuthService`, `UserService`, and `ChatService`. Each has one `ServiceImpl` implementation beside its interface; `ChatService` covers both conversation and message flows. HTTP input and output live in `dto/request` and `dto/response`. Shared `CreationResult<T>` carries internal creation/reuse metadata; controllers still send `ApiResponse` JSON. Conversation summary queries map directly to response DTOs in the repository without projection types. See [service contracts and package structure](backend-conventions.md#service-contracts-and-package-structure) for the implementation rules.

HTTP DTOs are mutable classes with explicit JSON names. DTO constraints validate request structure at HTTP and service boundaries; services retain business authorization and persistence decisions. Service contracts expose DTOs rather than persistence entities.

Users receive actionable Indonesian error messages. Stable HTTP status/error codes and field keys support client handling; internal technical diagnostics are kept in logs.

## Pagination response contract

History returns the message list directly in ApiResponse.data. Cursor metadata lives in the common pagination field: size and hasNext are always present; nextCursor is included only when another batch exists. Page, totalElements, totalPages, and hasPrevious are omitted in cursor mode, including on the first request without a cursor. Numbered pagination retains its existing page/size/totals/hasNext/hasPrevious fields and omits nextCursor. PaginationMeta uses nullable mode-specific fields with NON_NULL serialization. Services carry data and metadata through the reusable internal PaginatedResult<T>; controllers create the public ApiResponse envelope. This replaces the former nested data.items/data.nextCursor history shape.
