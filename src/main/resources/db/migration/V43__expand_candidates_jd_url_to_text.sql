-- Job descriptions can store multiple text sections as JSON in jd_url.

ALTER TABLE candidates
    ALTER COLUMN jd_url TYPE TEXT;
