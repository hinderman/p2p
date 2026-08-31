# Flyway migration location

The PostgreSQL base schema is provisioned database-first through the idempotent scripts in `C:MyProjects\.database`. Flyway baselines that existing schema at version `0` and applies the additive migrations in this directory at application startup.

Hibernate uses `ddl-auto: validate`; it validates mappings but never creates or changes the schema. Do not add the initial schema to this directory or enable generated DDL.

All schema evolution after the base schema must use this convention:

```text
V<version>__<description_in_snake_case>.sql
```

Applied migrations are immutable. Any schema change must be introduced as a new versioned script; do not edit the database-first base scripts after deployment.

`V1__financial_journal_and_integrity.sql` introduces the append-only financial journal. It assumes the base scripts have already been run. Before production rollout, back up the database and run Flyway validation in the release pipeline.
