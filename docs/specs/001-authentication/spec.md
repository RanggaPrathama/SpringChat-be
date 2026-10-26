# 001 — Authentication identity

## Decision

Accounts have a unique username, a unique email, and a password credential stored only as a password hash. The email is the sole login identifier; usernames are never used to authenticate. `displayName` is not a separate account field.

## Contract

- Registration accepts `username`, `email`, and `password`; registration does not automatically create a session.
- Request DTOs normalize username and email by trimming and lowercasing them before controller logic; passwords are passed unchanged.
- `POST /api/v1/auth/login` accepts `{ "email": "...", "password": "..." }` and never treats the email field as a username fallback.
- Credential failure returns the same generic unauthorized response whether the email is unknown or the password is incorrect.
- Each successful login creates an independent auth session with one usable refresh token.
- A successful refresh consumes the presented token and issues its replacement in the same session. Consumed token rows are retained for replay detection; only an unconsumed token from a non-revoked, unexpired session can refresh.
- Logout revokes the associated session and consumes every unconsumed refresh token in that session, including when logout is presented with an older token from that token rotation history. Repeating logout is safe.
- Logout invalidates refresh credentials immediately. Existing access JWTs remain usable until their own expiry; immediate access-token revocation is outside this contract.
- Passwords remain hashed and are never returned in API responses.
- `AuthController` keeps short HTTP action names; `AuthService` uses `registerUser`, `loginWithEmail`, `refreshSession`, and `logoutSession`. Expected authentication failures keep generic public responses. Service diagnostics never include credentials or tokens; transaction and logging rules are in [backend conventions](../../backend-conventions.md).

## Acceptance criteria

- A valid email/password pair authenticates regardless of email casing or surrounding whitespace.
- A username/password pair does not authenticate.
- An unknown email and an incorrect password produce the same public error response.
- Login and registration responses never expose a password or password hash.
- Repeated refresh-token rotation leaves at most one unconsumed token per session.
- Logout leaves no unconsumed refresh token in the revoked session, and refresh attempts using its old or current tokens are rejected.
