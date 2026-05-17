ALTER TABLE IF EXISTS events ADD COLUMN IF NOT EXISTS villain_definition_id BIGINT;
ALTER TABLE IF EXISTS events ADD COLUMN IF NOT EXISTS villain_name_snapshot VARCHAR(120);
ALTER TABLE IF EXISTS events ADD COLUMN IF NOT EXISTS villain_type_snapshot VARCHAR(20);
ALTER TABLE IF EXISTS events ADD COLUMN IF NOT EXISTS villain_hp_rule_type_snapshot VARCHAR(20);
ALTER TABLE IF EXISTS events ADD COLUMN IF NOT EXISTS villain_hp_rule_value_snapshot INTEGER;
ALTER TABLE IF EXISTS events ADD COLUMN IF NOT EXISTS villain_threat_rule_type_snapshot VARCHAR(20);
ALTER TABLE IF EXISTS events ADD COLUMN IF NOT EXISTS villain_threat_rule_value_snapshot INTEGER;

ALTER TABLE IF EXISTS event_groups ALTER COLUMN pod_id DROP NOT NULL;
ALTER TABLE IF EXISTS event_logs ALTER COLUMN action_type VARCHAR(40);

ALTER TABLE IF EXISTS inter_pod_cards ALTER COLUMN card_name VARCHAR(120);
ALTER TABLE IF EXISTS inter_pod_cards ADD COLUMN IF NOT EXISTS inter_pod_card_definition_id BIGINT;
ALTER TABLE IF EXISTS inter_pod_cards ADD COLUMN IF NOT EXISTS threat_max INTEGER DEFAULT 0;
ALTER TABLE IF EXISTS inter_pod_cards ADD COLUMN IF NOT EXISTS threat_current INTEGER DEFAULT 0;

ALTER TABLE IF EXISTS inter_pod_cards ALTER COLUMN threat_max SET NOT NULL;
ALTER TABLE IF EXISTS inter_pod_cards ALTER COLUMN threat_current SET NOT NULL;

ALTER TABLE IF EXISTS inter_pod_card_definitions ADD COLUMN IF NOT EXISTS image_path VARCHAR(500);
ALTER TABLE IF EXISTS inter_pod_card_definitions ADD COLUMN IF NOT EXISTS image_content_type VARCHAR(120);
ALTER TABLE IF EXISTS inter_pod_card_definitions ADD COLUMN IF NOT EXISTS default_threat_max INTEGER DEFAULT 0;

ALTER TABLE IF EXISTS inter_pod_card_definitions ALTER COLUMN default_threat_max SET NOT NULL;
