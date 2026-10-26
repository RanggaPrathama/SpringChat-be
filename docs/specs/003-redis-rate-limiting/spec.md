# 003 — Redis-backed authentication rate limiting

## Goal

Use Redis to share authentication rate-limit counters across application instances while preserving local development availability if Redis cannot be reached.

## Scope and decisions

- Redis is used only for login and registration rate-limit counters in this iteration. PostgreSQL remains authoritative for users, auth sessions, and refresh-token rotation. Profile caching, presence, message fan-out, and WebSocket broker replacement are out of scope.
- Use `spring-boot-starter-data-redis` with Spring Boot's default Lettuce client and auto-configured Spring Data Redis connection support.
- Preserve current limits: at most 5 login attempts per normalized email in a 15-minute fixed window; a successful login clears that email's counter. Allow at most 5 registrations per remote address per hour.
- Redis counters use atomic increment-and-first-expiry, with Redis TTL matching the limit window. Redis key material is HMAC-SHA-256 pseudonymized with a dedicated shared application secret of at least 32 UTF-8 bytes; raw emails and addresses are not written to Redis keys.
- On Redis data-access failure, use the existing in-process fixed-window limiter. This keeps the application available but means fallback limits are per instance and do not include attempts recorded only in Redis. Emit a warning once on transition to fallback and an info log once on recovery; never log identifiers or credentials.
- Development uses a non-persistent Redis service in `../../../docker-compose.yaml`, bound to localhost. The default `dev` profile connects to localhost and uses short configurable connection/command timeouts. Production connection settings and the HMAC secret are provided through environment variables; do not use the development secret in production.

## Configuration and operation

- Start local Redis with `docker compose up -d redis`; stop it with `docker compose stop redis`.
- Development defaults: `REDIS_HOST=localhost`, `REDIS_PORT=6379`, `REDIS_CONNECT_TIMEOUT=250ms`, `REDIS_TIMEOUT=500ms`.
- Production may configure `REDIS_URL` (default `redis://localhost:6379`) and must provide `RATE_LIMIT_KEY_SECRET`. Every application instance must use the same secret so it derives the same Redis keys.
- Redis is optional for application startup and request processing; failed operations use the local fallback. Rate-limit counters are ephemeral and are not persisted to disk.

## Acceptance criteria

- Login allows the first five attempts in a 15-minute window, rejects subsequent attempts, and resets the account counter after successful login.
- Registration allows five attempts per remote address per hour and rejects the sixth.
- Multiple limiter instances sharing Redis observe the same counter and expiry window.
- Email and address values do not appear in Redis keys or fallback logs.
- When Redis is unavailable, authentication remains available under local limits; when Redis recovers, the limiter resumes shared counters and logs recovery.
- Redis outage does not prevent application startup. No schema migration is required.

## Verification

- Unit tests cover threshold behavior, login reset, shared counters, and local fallback.
- Redis-backed integration tests verify Lua counter atomicity and expiry behavior against a real Redis instance.
- Run the application with Redis available and unavailable, confirming it starts in both cases and the logs never contain account identifiers.
