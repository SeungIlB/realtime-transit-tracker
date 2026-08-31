CREATE TABLE stop_pattern (
    id UUID PRIMARY KEY,
    line_id UUID NOT NULL REFERENCES transit_line (id),
    provider_pattern_id VARCHAR(100) NOT NULL,
    service_type VARCHAR(20) NOT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_stop_pattern_version
        UNIQUE (line_id, provider_pattern_id, valid_from),
    CONSTRAINT ck_stop_pattern_service_type
        CHECK (service_type IN ('LOCAL', 'EXPRESS', 'RAPID', 'CIRCULAR', 'SHUTTLE', 'UNKNOWN')),
    CONSTRAINT ck_stop_pattern_validity
        CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

CREATE TABLE stop_pattern_stop (
    stop_pattern_id UUID NOT NULL REFERENCES stop_pattern (id) ON DELETE CASCADE,
    stop_sequence INTEGER NOT NULL,
    stop_id UUID NOT NULL REFERENCES transit_stop (id),
    pickup_allowed BOOLEAN NOT NULL DEFAULT TRUE,
    dropoff_allowed BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (stop_pattern_id, stop_sequence),
    CONSTRAINT ck_pattern_stop_sequence
        CHECK (stop_sequence >= 0)
);
