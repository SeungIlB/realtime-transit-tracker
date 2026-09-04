UPDATE route_direction
SET active = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE line_id IN (
    SELECT tl.id
    FROM transit_line tl
    JOIN transit_provider tp ON tp.id = tl.provider_id
    WHERE tp.code = 'SEOUL_SUBWAY'
      AND tl.provider_line_id = '05호선'
);

UPDATE stop_pattern
SET active = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE line_id IN (
    SELECT tl.id
    FROM transit_line tl
    JOIN transit_provider tp ON tp.id = tl.provider_id
    WHERE tp.code = 'SEOUL_SUBWAY'
      AND tl.provider_line_id = '05호선'
);
