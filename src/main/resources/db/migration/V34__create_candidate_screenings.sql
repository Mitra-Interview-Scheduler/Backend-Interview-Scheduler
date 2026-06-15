CREATE TABLE candidate_screenings (
                                      id BIGSERIAL PRIMARY KEY,
                                      candidate_id BIGINT NOT NULL UNIQUE,
                                      is_project_specific BOOLEAN NOT NULL DEFAULT FALSE,
                                      project_name VARCHAR(255),
                                      region VARCHAR(255),
                                      engagement_type VARCHAR(50) NOT NULL DEFAULT 'FULL_TIME',
                                      duration INT,
                                      target_start_date VARCHAR(255),
                                      profile_source VARCHAR(100),
                                      referrer_name VARCHAR(255),
                                      screened_by VARCHAR(255),
                                      feedback TEXT,
                                      nature_of_recruitment VARCHAR(100),
                                      role_stretch VARCHAR(50),
                                      special_notes TEXT,
                                      department_id BIGINT,
                                      tier_id BIGINT,
                                      designation_id BIGINT,
                                      modified_at TIMESTAMP WITH TIME ZONE,

                                      CONSTRAINT fk_screening_candidate FOREIGN KEY (candidate_id) REFERENCES candidates(id) ON DELETE CASCADE,
                                      CONSTRAINT fk_screening_department FOREIGN KEY (department_id) REFERENCES departments(id),
                                      CONSTRAINT fk_screening_tier FOREIGN KEY (tier_id) REFERENCES tiers(id),
                                      CONSTRAINT fk_screening_designation FOREIGN KEY (designation_id) REFERENCES designations(id)
);

-- Indexing for fast dashboard lookup performance
CREATE INDEX idx_candidate_screenings_candidate ON candidate_screenings(candidate_id);