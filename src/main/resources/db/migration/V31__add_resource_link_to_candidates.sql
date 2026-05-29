-- Add resource link URL for candidate profile (e.g., shared drive folder/document link)
ALTER TABLE candidates
    ADD COLUMN IF NOT EXISTS resource_link VARCHAR(2000);
