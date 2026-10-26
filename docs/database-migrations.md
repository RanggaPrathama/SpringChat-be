# Development database baseline

The current migrations are a fresh-development baseline, not an upgrade path from the former V1-V8 history. One version belongs to one table; version numbers are unique across the entire migration location.

| Migration | Table |
| --- | --- |
| V1 | m_users |
| V2 | auth_sessions |
| V3 | refresh_tokens |
| V4 | conversations |
| V5 | messages |
| V6 | conversation_participants |
| V7 | idempotency_keys |

All identifiers and foreign keys use UUID. Users are created with `password_hash`, without `display_name`; refresh token hashes are `VARCHAR(64)` from creation. Conversation membership lives exclusively in `conversation_participants`. Messages contain no client-message or idempotency-key column: request deduplication belongs to `idempotency_keys`. Messages must be created before participants because the participant read marker has a composite foreign key to a message in the same conversation. Each file creates the final table, indexes, and constraints together; no column-upgrade migrations are needed in this baseline.

## Switching from the old development database

1. Stop the application and keep the existing database if its data is needed. These migration files do not migrate that data.
2. Create a new, empty development database, for example `springchat_dev_v2`, and set `DB_URL=jdbc:postgresql://localhost:5432/springchat_dev_v2` in the IntelliJ run configuration. Keep the appropriate database username and password in the environment.
3. Run a clean Maven build (`./mvnw clean test` or `mvnw.cmd clean test` on Windows), or clean the IDE build output. A plain incremental resource copy can leave deleted old SQL migrations in `target/classes/db/migration` and cause duplicate-version errors.
4. Start the application. Flyway creates the seven tables in order, then Hibernate validates the schema. The new `flyway_schema_history` should contain seven successful versioned migrations. An earlier six-table development baseline also requires a fresh database because the definition of `messages` has changed.

No live database reset, automatic Flyway clean, or disabling of validation is part of this change. Do not use `repair` to pretend the old schema matches this baseline: repair changes history/checksums, not table definitions.

## After this baseline is shared

Editing a migration that is already recorded in a database causes Flyway checksum or history validation errors. While development databases are disposable, baseline changes must be coordinated with creating fresh databases. Once database data must be retained, leave applied migrations immutable and append the next version (for example V8) with the required ALTER statements. A migration version describes a schema change; one table per file is the initial-baseline convention, not a Flyway requirement.
