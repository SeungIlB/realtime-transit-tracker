ALTER TABLE arrival_prediction_observation
    ADD COLUMN alighting_stop_status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN';

UPDATE arrival_prediction_observation
SET alighting_stop_status = CASE
    WHEN alighting_stop_confirmed THEN 'STOPS'
    ELSE 'UNKNOWN'
END;

ALTER TABLE arrival_prediction_observation
    ADD CONSTRAINT ck_arrival_alighting_stop_status
        CHECK (alighting_stop_status IN ('STOPS', 'SKIPS', 'UNKNOWN', 'NOT_REQUESTED'));

CREATE INDEX ix_arrival_prediction_alighting_status
    ON arrival_prediction_observation (
        boarding_stop_id,
        requested_alighting_stop_id,
        alighting_stop_status,
        received_at DESC
    );
