-- Simple profile lists (Phase 2E): key coursework and areas of interest.
ALTER TABLE candidate_profiles
    ADD COLUMN coursework        TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN areas_of_interest TEXT[] NOT NULL DEFAULT '{}';
