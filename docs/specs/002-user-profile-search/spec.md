# 002 — Username profile and directory search

## Decision

`username` is the canonical public identity. It remains case-insensitively unique, can be changed by its owner, and is used in profiles, directory search, and conversation summaries. `displayName` is removed. `avatarUrl` remains optional.

## Contract

- Registration accepts `username`, `email`, and `password`.
- Request DTOs sanitize markup from username and trim/lowercase username and email before controller and service logic. Profile updates also trim `avatarUrl`; an empty value remains available to clear the avatar.
- `GET /api/v1/users/me` and `GET /api/v1/users/{id}` return `id`, `username`, `email`, and `avatarUrl`.
- `PATCH /api/v1/users/me` accepts `username` and/or `avatarUrl`; email and password are not editable through this endpoint.
- `GET /api/v1/users?search=...` trims the search value before service logic, searches case-insensitive username substrings only, requires at least two characters, excludes the current user, and preserves the existing pagination defaults and maximum.
- User objects in search and conversation responses use the same `UserResponse` fields: `id`, `username`, `email`, and `avatarUrl`. Password hashes and `displayName` are never returned.
- Controller actions distinguish directory `search`, `getById`, and `getMe`; the `UserService` interface declares resource-specific actions (`searchUsers`, `getUserById`, `getOwnProfile`, `updateOwnProfile`) implemented by `UserServiceImpl` beside the interface in the flat `service` package. HTTP input/output classes live in `dto/request` and `dto/response`; registration returns UserResponse and authentication reads credentials internally through UserRepository. See [backend conventions](../../backend-conventions.md).

## Data migration

The fresh-development baseline creates `m_users` without `display_name` in V1. Existing databases must be handled according to `docs/database-migrations.md`; the baseline does not convert or discard retained account data. Username values remain authoritative, and former display names are not copied into usernames.

## Acceptance criteria

- Username updates are normalized consistently and rejected if another account already owns the same username, ignoring case.
- Directory results match username only; a former display-name value is not a searchable alias.
- Profile, directory, user-detail, and conversation JSON contain email and no password or `displayName` field.
- The migrated schema validates without a `display_name` column.

## DTO and validation implementation

HTTP DTOs use Lombok @Data classes and explicit camelCase @JsonProperty names. Normalizing setters run before Bean Validation. Field and cross-field constraints protect both @Valid HTTP input and @Validated service calls; database and actor-dependent business rules remain in services. Entity mapping is explicit in module mappers. See [backend conventions](../../backend-conventions.md).

Public errors use actionable Indonesian wording without internal enum names, Java method paths, or database details. Status codes and JSON field names are unchanged; see the user-facing error policy in [backend conventions](../../backend-conventions.md).
