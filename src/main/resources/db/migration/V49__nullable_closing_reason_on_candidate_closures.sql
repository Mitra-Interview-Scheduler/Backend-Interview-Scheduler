-- V49__nullable_closing_reason_on_candidate_closures.sql

ALTER TABLE candidate_closures
    ALTER COLUMN closing_reason_id DROP NOT NULL;
