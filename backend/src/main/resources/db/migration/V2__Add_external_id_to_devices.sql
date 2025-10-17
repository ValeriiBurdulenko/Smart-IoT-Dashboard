ALTER TABLE devices
    ADD COLUMN external_id VARCHAR(255);

ALTER TABLE devices
    ALTER COLUMN external_id SET NOT NULL;

ALTER TABLE devices
    ADD CONSTRAINT uk_devices_external_id UNIQUE (external_id);