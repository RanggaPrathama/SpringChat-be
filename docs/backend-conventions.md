# Backend development conventions

## Names and layer boundaries

Controllers use short names that are clear in their class and route: `search`, `getById`, `getMe`, `create`, `send`, `history`, `markRead`. A generic `get` or `getAll` is insufficient when it hides whether the action is detail retrieval, search, or paginated listing. A service method names the action and resource: `searchUsers`, `getUserById`, `createConversation`, `listConversations`, `sendMessage`, `getMessageHistory`. Use a singular resource when one record is created. Helpers should state their purpose, such as `requireUserById`.

Controllers handle HTTP input, authentication identity, validation, and response status. Services enforce authorization and business rules, coordinate repositories, and define transaction boundaries. Repository methods contain persistence queries and locking. The shared idempotency component retains its atomic PostgreSQL reservation SQL because that is its infrastructure responsibility. Request DTOs normalize or sanitize supported fields before service logic; response DTOs define the wire shape.

## Errors and transaction boundaries

- Throw a domain exception for expected failure: invalid input (`BadRequestException`, 400), unavailable or inaccessible resource (`ResourceNotFoundException`, 404), duplicate resource (`DuplicateResourceException`, 409), or exceeded limit (`TooManyRequestsException`, 429). Do not expose whether a protected conversation exists to a non-member.
- The global handler maps PostgreSQL SQLSTATE `23505` to a generic 409 as a safety net for unique-constraint races. Other integrity violations and unexpected exceptions become generic 500 responses and are logged with stack traces. Never return raw database messages to clients.
- Put `@Transactional` on a public service use case that writes multiple related rows. A normal return commits when the outermost transaction completes; an unchecked exception escaping it rolls back by default. There is no manual commit or rollback. If a future checked exception must roll back, declare an explicit rollback rule.
- `save` does not commit. Use `saveAndFlush` when a later JDBC operation in the same transaction must observe the new row, as in message creation before idempotency completion. Keep reservation, message, activity update, and completion in one transaction. A replay does no new writes or publication.
- Keep external side effects after commit. Message WebSocket delivery uses `@TransactionalEventListener(phase = AFTER_COMMIT)`. Do not log a write as successful before commit. A controller can log a low-volume success after its service call returns; an after-commit listener can log a committed event.

## Logging

- `TRACE`: temporary fine-grained diagnostics when explicitly enabled. `DEBUG`: branch decisions and IDs useful during development. `INFO`: low-volume lifecycle events or confirmed business milestones. `WARN`: unusual but handled conditions, such as refresh-token replay or a limiter fallback. `ERROR`: unexpected failures requiring investigation, with one stack trace at the exception boundary.
- Do not log passwords, raw or hashed tokens, email addresses, message content, cookies, request bodies, or authorization headers. Use IDs and counts where useful. Avoid a WARN per invalid login or an INFO per message; both can flood logs. Do not repeat the same stack trace in service and global handler.
- Development writes to `logs/springchat.log`, with application DEBUG and root INFO; see `application-dev.yaml`. Production logging follows its deployment configuration. Rotated local logs are ignored by Git.

## Verification when changing a flow

Keep controller contract tests for routes and responses; use service tests for business branches. Use PostgreSQL integration tests for locks, unique constraints, mixed JPA/JDBC transactions, idempotency retries, rollback, read markers, and after-commit delivery. Run Maven tests with Docker available for Testcontainers. Update the relevant feature spec whenever its business or wire contract changes; update this guide when a cross-feature coding rule changes.
