ALTER TABLE IF EXISTS events ADD COLUMN IF NOT EXISTS villain_definition_id BIGINT;
ALTER TABLE IF EXISTS events ADD COLUMN IF NOT EXISTS villain_name_snapshot VARCHAR(120);
ALTER TABLE IF EXISTS events ADD COLUMN IF NOT EXISTS villain_type_snapshot VARCHAR(20);
ALTER TABLE IF EXISTS events ADD COLUMN IF NOT EXISTS villain_hp_rule_type_snapshot VARCHAR(20);
ALTER TABLE IF EXISTS events ADD COLUMN IF NOT EXISTS villain_hp_rule_value_snapshot INTEGER;
ALTER TABLE IF EXISTS events ADD COLUMN IF NOT EXISTS villain_threat_rule_type_snapshot VARCHAR(20);
ALTER TABLE IF EXISTS events ADD COLUMN IF NOT EXISTS villain_threat_rule_value_snapshot INTEGER;

ALTER TABLE IF EXISTS inter_pod_cards ALTER COLUMN card_name VARCHAR(120);
ALTER TABLE IF EXISTS inter_pod_cards ADD COLUMN IF NOT EXISTS inter_pod_card_definition_id BIGINT;
