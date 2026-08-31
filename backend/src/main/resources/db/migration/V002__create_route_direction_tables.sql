CREATE TABLE route_direction (
    id UUID PRIMARY KEY,
    line_id UUID NOT NULL REFERENCES transit_line (id),
    provider_direction_id VARCHAR(100) NOT NULL,
    origin_stop_id UUID REFERENCES transit_stop (id),
    terminal_stop_id UUID REFERENCES transit_stop (id),
    representative_next_stop_id UUID REFERENCES transit_stop (id),
    display_name VARCHAR(150) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_direction_provider_resource
        UNIQUE (line_id, provider_direction_id),
    CONSTRAINT ck_direction_distinct_terminal
        CHECK (origin_stop_id IS NULL OR terminal_stop_id IS NULL OR origin_stop_id <> terminal_stop_id)
);

CREATE TABLE directed_stop (
    id UUID PRIMARY KEY,
    line_id UUID NOT NULL REFERENCES transit_line (id),
    direction_id UUID NOT NULL REFERENCES route_direction (id) ON DELETE CASCADE,
    stop_id UUID NOT NULL REFERENCES transit_stop (id),
    stop_sequence INTEGER NOT NULL,
    next_stop_id UUID REFERENCES transit_stop (id),
    platform_id VARCHAR(100),
    display_direction VARCHAR(150),
    segment_id VARCHAR(100),
    CONSTRAINT uq_directed_stop_sequence
        UNIQUE (direction_id, stop_sequence),
    CONSTRAINT ck_directed_stop_sequence
        CHECK (stop_sequence >= 0),
    CONSTRAINT ck_directed_stop_next
        CHECK (next_stop_id IS NULL OR next_stop_id <> stop_id)
);
