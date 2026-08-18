# Flyway migration location

This directory is intentionally empty for the initial release. The PostgreSQL schema is provisioned database-first through the idempotent scripts in `C:\Proyect\.database`.

Hibernate uses `ddl-auto: validate`; it validates mappings but never creates or changes the schema. Do not add the initial schema to this directory or enable generated DDL.

If the project later adopts versioned database evolution, Flyway scripts must use this convention:

```text
V<version>__<description_in_snake_case>.sql
```

Applied migrations are immutable. Any schema change must be introduced as a new versioned script after the team explicitly adopts that delivery model.
