-- Candidate files move to the org-owned "Mitra Recruitment" Shared Drive.
-- Candidates get a per-candidate Drive folder; documents keep metadata but point at Drive.

ALTER TABLE candidates ADD COLUMN IF NOT EXISTS drive_folder_id VARCHAR(255);

ALTER TABLE candidate_documents ADD COLUMN IF NOT EXISTS drive_file_id  VARCHAR(255);
ALTER TABLE candidate_documents ADD COLUMN IF NOT EXISTS web_view_link  VARCHAR(1024);

-- Drive is the source of truth for the bytes now; the legacy blob becomes optional.
-- (Greenfield: existing blobs are negligible and left as-is; the column can be dropped
--  entirely in a later migration once nothing reads it.)
ALTER TABLE candidate_documents ALTER COLUMN file_data DROP NOT NULL;
