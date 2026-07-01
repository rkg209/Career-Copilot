---
description: Create the next Flyway migration SQL file with the correct version number.
---

Read CLAUDE.md for migration rules (additive-only, never DROP TABLE/COLUMN without DEFAULT).

Migration description: $ARGUMENTS

If no description provided, ask: "What does this migration do? (e.g., 'add experience_entries table', 'add vector column to job_postings')"

Steps:

1. Scan existing migrations to find the next version number:
   List all files in `src/main/resources/db/migration/` matching `V*.sql`.
   Find the highest version number N. The new migration will be V{N+1}.

   If the directory does not exist yet, create it. The first migration is V1.

2. Determine the filename:
   - Description from $ARGUMENTS → convert to snake_case
   - Full filename: `V{N+1}__{snake_case_description}.sql`
   - Example: "add experience entries table" → `V3__add_experience_entries_table.sql`

3. Create the file at `src/main/resources/db/migration/V{N+1}__{description}.sql`.

4. Based on the description, generate appropriate SQL. Follow these rules:

   **General DDL rules:**
   - All tables must have: `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`, `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`, `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`
   - Use `TIMESTAMPTZ` not `TIMESTAMP` for all timestamps
   - Foreign keys: add explicit `REFERENCES` constraints
   - Indexes: add indexes for all foreign key columns and columns used in WHERE clauses
   - Never DROP TABLE, DROP COLUMN, or add NOT NULL without DEFAULT on an existing table
   - Prefer `ALTER TABLE ... ADD COLUMN ... DEFAULT ...` if adding to existing table

   **pgvector DDL (use when adding vector columns):**
   ```sql
   -- Enable extension (safe to repeat)
   CREATE EXTENSION IF NOT EXISTS vector;

   -- Vector column example
   embedding vector(1536)

   -- IVFFlat index for approximate nearest neighbor search
   CREATE INDEX {table}_embedding_ivfflat_idx
       ON {table} USING ivfflat (embedding vector_cosine_ops)
       WITH (lists = 100);
   -- Note: ivfflat index requires data to be present first in prod.
   -- For empty tables at migration time, index is created immediately.
   -- For tables with existing data, consider CONCURRENTLY in a separate migration.
   ```

   **pgvector dimension guidance:**
   - OpenAI text-embedding-3-small → 1536 dimensions
   - OpenAI text-embedding-3-large → 3072 dimensions
   - Ollama nomic-embed-text → 768 dimensions
   - Use 1536 as default (matches text-embedding-3-small)

5. After creating the file, print:
   "Created migration: src/main/resources/db/migration/V{N+1}__{description}.sql

   Review the SQL before running. Key checks:
   - Is this additive only? (no DROP, no NOT NULL without DEFAULT on existing rows)
   - Are indexes correct?
   - Are foreign key references to existing tables?

   To apply: ./mvnw spring-boot:run (Flyway runs on startup) or flyway:migrate"
