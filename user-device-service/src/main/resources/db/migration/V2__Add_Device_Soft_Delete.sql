ALTER TABLE devices
    ADD COLUMN is_active BOOLEAN DEFAULT true NOT NULL;

ALTER TABLE devices
    ADD COLUMN deactivated_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_devices_active_device_id
    ON devices (device_id)
    WHERE is_active = true;