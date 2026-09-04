ALTER TABLE arrival_prediction_observation
    ADD COLUMN requested_alighting_stop_id UUID REFERENCES transit_stop (id),
    ADD COLUMN alighting_stop_confirmed BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX ix_arrival_prediction_alighting_confirmation
    ON arrival_prediction_observation (
        boarding_stop_id,
        requested_alighting_stop_id,
        alighting_stop_confirmed,
        received_at DESC
    );
