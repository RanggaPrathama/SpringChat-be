# Backend development conventions

## Names and layer boundaries

Controllers use short names that are clear in their class and route: `search`, `getById`, `getMe`, `create`, `send`, `history`, `markRead`. A generic `get` or `getAll` is insufficient when it hides whether the action is detail retrieval, search, or paginated listing. A service method names the action and resource: `searchUsers`, `getUserById`, `createConversation`, `listConversations`, `sendMessage`, `getMessageHistory`. Use a singular resource when one record is created. Helpers should state their purpose, such as `requireUserById`.

Controllers handle HTTP input, authentication identity, validation, and response status. Services enforce authorization and business rules, coordinate repositories, and define transaction boundaries. Repository methods contain persistence queries and locking. The shared idempotency component retains its atomic PostgreSQL reservation SQL because that is its infrastructure responsibility. Request DTOs normalize or sanitize supported fields before service logic; response DTOs define the wire shape.

## Service contracts and package structure

The three business modules expose `AuthService`, `UserService`, and `ChatService` interfaces in their `service` packages. Each implementation lives next to its interface in the same package: `AuthServiceImpl`, `UserServiceImpl`, and `ChatServiceImpl`. There are no implementation or result subfolders. Chat owns both conversation and message use cases; its two controllers inject the same `ChatService` contract. The infrastructure helpers for JWT, rate limiting, and idempotency are outside this convention.

Interfaces declare complete parameter and result types, with no HTTP response builders or implementation helpers. Controllers and other services inject interfaces. Implementations carry `@Service`, dependency injection, logging, public use-case transaction annotations, and `@Override`; helpers stay private. A single implementation per contract needs no `@Primary` or `@Qualifier`.

Within each module:

- `dto/request` contains HTTP input records and their normalization and validation rules.
- `dto/response` contains HTTP output records. DTO names and JSON fields remain stable when packages move.
- `IssuedTokens` is an auth-specific internal output in the flat auth `service` package. Its raw refresh credentials are for the controller's cookie handling and must never be serialized as a response body.
- `event` and `enums` contain application events and shared domain enums rather than mixing them into HTTP DTOs.

Reusable creation metadata uses `common/response/CreationResult<T>(T data, boolean created)`. Conversation creation returns `CreationResult<ConversationDetailResponse>` and message sending returns `CreationResult<MessageResponse>`. Controllers use `created()` to choose 201 for new resources or 200 for reuse/replay, and wrap `data()` in `ApiResponse`. `CreationResult` is not serialized into the HTTP body; the public JSON contains no extra `created` field.

Conversation summaries are mapped directly from SQL rows to `ConversationSummaryResponse` by `ConversationQueryRepository`; there is no projection type. The repository owns parameterized data and total-count queries, nested response mapping, and pagination ordered by activity and conversation UUID descending. It uses the same transaction/DataSource as JPA and flushes pending JPA changes before JDBC reads inside a write transaction, preserving read-your-writes without committing. Services coordinate the query rather than mapping database rows.

HTTP DTOs and the common ApiResponse/PaginationMeta envelopes are mutable classes with Lombok @Data and explicit camelCase @JsonProperty names. Request constructors call setters; normalizing setters preserve sanitization for both JSON binding and programmatic mutation. Passwords are unchanged by normalization, write-only in JSON, and excluded from toString. Internal CreationResult, IssuedTokens, events, and cursors remain records.

Field validation lives on DTO fields. Custom class constraints validate DIRECT/GROUP shape, duplicate members, and non-empty profile patches, without querying repositories. Controllers use @Valid; service interfaces declare @Valid/@NotNull and scalar constraints, and implementations use @Validated. Do not redeclare parameter constraints on overrides. Both HTTP and proxied internal callers receive validation; plain constructors and direct implementation instances do not automatically invoke Bean Validation. Validation failures return 400. Services retain actor-specific authorization, user availability, uniqueness, idempotency, and read-marker rules.

UserService.registerUser returns UserResponse; credential lookup is internal to AuthService through UserRepository. No entity is returned from business-service interfaces or HTTP endpoints. Manual UserMapper/ChatMapper functions whitelist persistence and response fields, without repository access or transaction logic. Password hashing occurs before entity mapping. Unit tests separate DTO validation from business decisions; Spring contract tests verify proxy validation and transactions.

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

`ServiceContractsTest` checks that each interface resolves to one transactional bean and that calls through the interfaces retain commit, rollback, and read-only behavior. It runs without Docker; the database and Redis integration tests still require their Testcontainers dependencies.

## User-facing error messages

Public error messages are Indonesian, describe the user's task, and suggest a practical next action. Validation messages say "Pilih satu orang untuk memulai percakapan pribadi", rather than exposing internal enum names, Java types, or parameter paths. Technical error codes and JSON field keys remain stable for clients; service validation reports the same field names as HTTP validation. Framework exception details, database diagnostics, and stack traces stay out of HTTP bodies. Unexpected errors use a safe retry message, while logs retain technical diagnostics. Success messages are outside this wording update.

## Pagination response contract

History returns the message list directly in ApiResponse.data. Cursor metadata lives in the common pagination field: size and hasNext are always present; nextCursor is included only when another batch exists. Page, totalElements, totalPages, and hasPrevious are omitted in cursor mode, including on the first request without a cursor. Numbered pagination retains its existing page/size/totals/hasNext/hasPrevious fields and omits nextCursor. PaginationMeta uses nullable mode-specific fields with NON_NULL serialization. Services carry data and metadata through the reusable internal PaginatedResult<T>; controllers create the public ApiResponse envelope. This replaces the former nested data.items/data.nextCursor history shape.
