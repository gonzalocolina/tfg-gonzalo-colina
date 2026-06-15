-- Migración defensiva desde el esquema anterior a la reestructuración de events.
-- Con ddl-auto=update Hibernate evoluciona el esquema; este script cubre bases H2 persistentes
-- o bases H2 persistentes que aún no tengan las columnas nuevas.
ALTER TABLE IF EXISTS inter_pod_cards RENAME TO inter_pod_card_instances;
ALTER TABLE IF EXISTS event_villain RENAME TO villain_instances;
ALTER TABLE IF EXISTS users DROP COLUMN IF EXISTS role;
ALTER TABLE IF EXISTS users DROP COLUMN IF EXISTS enabled;
ALTER TABLE IF EXISTS event_groups DROP COLUMN IF EXISTS representative_code;
ALTER TABLE IF EXISTS event_groups DROP COLUMN IF EXISTS observer_code;
ALTER TABLE IF EXISTS events ADD COLUMN IF NOT EXISTS villain_damage_taken INTEGER NOT NULL DEFAULT 0;
CREATE TABLE IF NOT EXISTS selected_inter_pod_card_definitions (
    event_id BIGINT NOT NULL,
    inter_pod_card_definition_id BIGINT NOT NULL,
    PRIMARY KEY (event_id, inter_pod_card_definition_id)
);
ALTER TABLE IF EXISTS events DROP COLUMN IF EXISTS selected_inter_pod_card_definition_ids;
ALTER TABLE IF EXISTS inter_pod_card_definitions DROP COLUMN IF EXISTS default_status;
ALTER TABLE IF EXISTS inter_pod_card_definitions ADD COLUMN IF NOT EXISTS hp_rule_type VARCHAR(20) NOT NULL DEFAULT 'MULTIPLIER';
ALTER TABLE IF EXISTS inter_pod_card_definitions ADD COLUMN IF NOT EXISTS threat_rule_type VARCHAR(20) NOT NULL DEFAULT 'MULTIPLIER';

-- Unificación de métrica inter-pod (HP / amenaza → metric).
ALTER TABLE IF EXISTS inter_pod_card_definitions ADD COLUMN IF NOT EXISTS default_metric_max INTEGER NOT NULL DEFAULT 0;
ALTER TABLE IF EXISTS inter_pod_card_definitions ADD COLUMN IF NOT EXISTS rule_type VARCHAR(20) NOT NULL DEFAULT 'MULTIPLIER';

UPDATE inter_pod_card_definitions
SET default_metric_max = CASE
        WHEN card_category = 'ESBIRRO' THEN default_hp_max
        WHEN card_category = 'PLAN' THEN default_threat_max
        ELSE 0
    END,
    rule_type = CASE
        WHEN card_category = 'ESBIRRO' THEN hp_rule_type
        WHEN card_category = 'PLAN' THEN threat_rule_type
        ELSE 'MULTIPLIER'
    END
WHERE default_metric_max = 0;

ALTER TABLE IF EXISTS inter_pod_card_definitions DROP COLUMN IF EXISTS default_hp_max;
ALTER TABLE IF EXISTS inter_pod_card_definitions DROP COLUMN IF EXISTS default_threat_max;
ALTER TABLE IF EXISTS inter_pod_card_definitions DROP COLUMN IF EXISTS hp_rule_type;
ALTER TABLE IF EXISTS inter_pod_card_definitions DROP COLUMN IF EXISTS threat_rule_type;

ALTER TABLE IF EXISTS inter_pod_card_instances ADD COLUMN IF NOT EXISTS metric_max INTEGER NOT NULL DEFAULT 0;
ALTER TABLE IF EXISTS inter_pod_card_instances ADD COLUMN IF NOT EXISTS metric_current INTEGER NOT NULL DEFAULT 0;

UPDATE inter_pod_card_instances
SET metric_max = CASE
        WHEN card_category = 'ESBIRRO' THEN hp_max
        WHEN card_category = 'PLAN' THEN threat_max
        ELSE 0
    END,
    metric_current = CASE
        WHEN card_category = 'ESBIRRO' THEN hp_current
        WHEN card_category = 'PLAN' THEN threat_current
        ELSE 0
    END
WHERE metric_max = 0 AND metric_current = 0;

ALTER TABLE IF EXISTS inter_pod_card_instances DROP COLUMN IF EXISTS hp_max;
ALTER TABLE IF EXISTS inter_pod_card_instances DROP COLUMN IF EXISTS hp_current;
ALTER TABLE IF EXISTS inter_pod_card_instances DROP COLUMN IF EXISTS threat_max;
ALTER TABLE IF EXISTS inter_pod_card_instances DROP COLUMN IF EXISTS threat_current;
