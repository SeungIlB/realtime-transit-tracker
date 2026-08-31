CREATE INDEX ix_direction_line_active
    ON route_direction (line_id, active);

CREATE INDEX ix_directed_stop_lookup
    ON directed_stop (line_id, stop_id, direction_id);

CREATE INDEX ix_pattern_line_active
    ON stop_pattern (line_id, active, valid_from DESC);

CREATE INDEX ix_pattern_stop_lookup
    ON stop_pattern_stop (stop_id, stop_pattern_id);

CREATE INDEX ix_raw_observation_request_time
    ON raw_observation (provider_id, endpoint, request_key, received_at DESC);

CREATE INDEX ix_raw_observation_expiry
    ON raw_observation (expires_at)
    WHERE expires_at IS NOT NULL;

CREATE INDEX ix_vehicle_observation_line_time
    ON vehicle_run_observation (line_id, observed_at DESC);

CREATE INDEX ix_vehicle_observation_identity_time
    ON vehicle_run_observation (line_id, provider_vehicle_id, observed_at DESC);

CREATE INDEX ix_vehicle_observation_current_stop_time
    ON vehicle_run_observation (current_stop_id, observed_at DESC)
    WHERE current_stop_id IS NOT NULL;

CREATE INDEX ix_arrival_boarding_time
    ON arrival_prediction_observation (boarding_stop_id, observed_at DESC);

CREATE INDEX ix_arrival_vehicle_observation
    ON arrival_prediction_observation (vehicle_run_observation_id)
    WHERE vehicle_run_observation_id IS NOT NULL;
