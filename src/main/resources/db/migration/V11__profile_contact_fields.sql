-- Resume rendering (Phase 3) needs the candidate's display name and phone.
-- Additive and nullable — safe on existing rows.
ALTER TABLE candidate_profiles
    ADD COLUMN full_name TEXT,
    ADD COLUMN phone     TEXT;
