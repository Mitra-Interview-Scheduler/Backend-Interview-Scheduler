CREATE TABLE departments (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(50) UNIQUE
);

CREATE TABLE designations (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    hierarchy_level INT NOT NULL,
    is_active BOOLEAN DEFAULT TRUE
);

CREATE TABLE designation_interview_rules (
    id BIGSERIAL PRIMARY KEY,
    interviewer_designation_id BIGINT REFERENCES designations(id),
    candidate_designation_id BIGINT REFERENCES designations(id),
    allowed BOOLEAN DEFAULT TRUE,
    UNIQUE (interviewer_designation_id, candidate_designation_id)
);

CREATE TABLE technologies (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    category VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE
);

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255),
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    phone VARCHAR(50),
    profile_picture_url VARCHAR(512),
    role VARCHAR(50) NOT NULL,
    department_id BIGINT REFERENCES departments(id),
    current_designation_id BIGINT REFERENCES designations(id),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE interviewer_technologies (
    id BIGSERIAL PRIMARY KEY,
    interviewer_id BIGINT REFERENCES users(id),
    technology_id BIGINT REFERENCES technologies(id),
    years_of_experience INT,
    is_active BOOLEAN DEFAULT TRUE
);

CREATE TABLE availability_slots (
    id BIGSERIAL PRIMARY KEY,
    interviewer_id BIGINT REFERENCES users(id),
    day_of_week VARCHAR(20),
    start_time TIME,
    end_time TIME,
    specific_date DATE,
    is_recurring BOOLEAN DEFAULT TRUE,
    valid_from DATE,
    valid_until DATE,
    status VARCHAR(50) DEFAULT 'AVAILABLE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE slot_blocks (
    id BIGSERIAL PRIMARY KEY,
    interviewer_id BIGINT REFERENCES users(id),
    start_date_time TIMESTAMP,
    end_date_time TIMESTAMP,
    reason TEXT,
    type VARCHAR(50)
);

CREATE TABLE interview_requests (
    id BIGSERIAL PRIMARY KEY,
    candidate_name VARCHAR(255),
    candidate_designation_id BIGINT REFERENCES designations(id),
    preferred_date DATE,
    preferred_start TIME,
    preferred_end TIME,
    requested_by_id BIGINT REFERENCES users(id),
    assigned_interviewer_id BIGINT REFERENCES users(id),
    status VARCHAR(50) DEFAULT 'PENDING',
    is_urgent BOOLEAN DEFAULT FALSE,
    notes TEXT,
    deadline TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    responded_at TIMESTAMP
);

CREATE TABLE interview_requests_technologies (
    interview_request_id BIGINT REFERENCES interview_requests(id),
    technology_id BIGINT REFERENCES technologies(id),
    PRIMARY KEY (interview_request_id, technology_id)
);

CREATE TABLE urgent_interview_broadcasts (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255),
    message TEXT,
    candidate_designation_id BIGINT REFERENCES designations(id),
    target_department_id BIGINT REFERENCES departments(id),
    deadline TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE urgent_interview_broadcasts_technologies (
    urgent_interview_broadcast_id BIGINT REFERENCES urgent_interview_broadcasts(id),
    technology_id BIGINT REFERENCES technologies(id),
    PRIMARY KEY (urgent_interview_broadcast_id, technology_id)
);

CREATE TABLE urgent_interview_responses (
    id BIGSERIAL PRIMARY KEY,
    broadcast_id BIGINT REFERENCES urgent_interview_broadcasts(id),
    interviewer_id BIGINT REFERENCES users(id),
    can_attend BOOLEAN,
    proposed_slot VARCHAR(255),
    notes TEXT
);

CREATE TABLE interview_schedules (
    id BIGSERIAL PRIMARY KEY,
    request_id BIGINT REFERENCES interview_requests(id),
    interviewer_id BIGINT REFERENCES users(id),
    start_date_time TIMESTAMP,
    end_date_time TIMESTAMP,
    meeting_link VARCHAR(512),
    location VARCHAR(255),
    status VARCHAR(50) DEFAULT 'SCHEDULED',
    completed_at TIMESTAMP
);

CREATE TABLE interview_feedback (
    id BIGSERIAL PRIMARY KEY,
    schedule_id BIGINT REFERENCES interview_schedules(id),
    technical_score INT,
    communication_score INT,
    comments TEXT,
    recommended BOOLEAN,
    submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    recipient_id BIGINT REFERENCES users(id),
    subject VARCHAR(255),
    message TEXT,
    type VARCHAR(50),
    sent BOOLEAN DEFAULT FALSE,
    sent_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_read BOOLEAN DEFAULT FALSE
);