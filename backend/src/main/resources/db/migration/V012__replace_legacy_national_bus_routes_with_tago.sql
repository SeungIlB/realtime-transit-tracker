UPDATE transit_line
SET active = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE provider_id = (
    SELECT id
    FROM transit_provider
    WHERE code = 'NATIONAL_PRECISION_BUS'
)
  AND provider_line_id NOT LIKE 'TAGO:%';

UPDATE transit_provider
SET display_name = '전국 버스(TAGO)',
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'NATIONAL_PRECISION_BUS';
