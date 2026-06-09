# Diagrama entidad-relación de la base de datos

Este diagrama está verificado contra la base H2 local `backend/data/god_of_lies.mv.db` y alineado con el modelo persistente actual del backend (`backend/src/main/java/com/tfg/gonzalo/backend/model`).

Notas rápidas:
- Todas las tablas de entidad heredan de `BaseEntity`, por lo que incluyen `id`, `created_at` y `updated_at`.
- Se han incluido también las tablas auxiliares generadas por relaciones `@ManyToMany` y `@ElementCollection`.
- El archivo `docs/diagrama-entidad-relacion.puml` es la versión UML principal para edición/exportación.
- El contenido refleja el esquema persistido actual del proyecto, no el diagrama de referencia que me pasaste.

```mermaid
erDiagram
    event_organizers {
        BIGINT id PK
        TIMESTAMP created_at
        TIMESTAMP updated_at
        VARCHAR username UK
        VARCHAR email UK
        VARCHAR password_hash
        ENUM role
        BOOLEAN enabled
    }

    events {
        BIGINT id PK
        TIMESTAMP created_at
        TIMESTAMP updated_at
        VARCHAR name
        INT configured_player_count
        INT time_limit_minutes
        ENUM status
        ENUM result
        VARCHAR event_code UK
        BIGINT organizer_id FK
        BIGINT villain_definition_id FK
        VARCHAR villain_name_snapshot
        ENUM villain_type_snapshot
        ENUM villain_hp_rule_type_snapshot
        INT villain_hp_rule_value_snapshot
        ENUM villain_threat_rule_type_snapshot
        INT villain_threat_rule_value_snapshot
        TIMESTAMP started_at
        TIMESTAMP finished_at
    }

    game_states {
        BIGINT id PK
        TIMESTAMP created_at
        TIMESTAMP updated_at
        BIGINT event_id FK UK
        INT villain_hp_max
        INT villain_hp_current
        INT scheme_threat_target
        INT scheme_threat_current
        INT timer_total_seconds
        INT timer_remaining_seconds
        ENUM timer_status
        ENUM result
        ENUM finish_cause
        INT total_reported_villain_damage
        BOOLEAN villain_half_hp_notified
        BOOLEAN villain_quarter_hp_notified
        BOOLEAN threat_half_notified
        BOOLEAN threat_three_quarter_notified
        BOOLEAN timer_thirty_minutes_notified
        BOOLEAN timer_fifteen_minutes_notified
        BOOLEAN timer_five_minutes_notified
        TIMESTAMP timer_started_at
        TIMESTAMP timer_paused_at
        TIMESTAMP finished_at
    }

    pods {
        BIGINT id PK
        TIMESTAMP created_at
        TIMESTAMP updated_at
        BIGINT event_id FK
        VARCHAR name
        INT sort_order
    }

    player_table {
        BIGINT id PK
        TIMESTAMP created_at
        TIMESTAMP updated_at
        BIGINT event_id FK
        BIGINT pod_id FK
        VARCHAR identifier
        INT player_count
        VARCHAR representative_code UK
        VARCHAR observer_code UK
    }

    invitations {
        BIGINT id PK
        TIMESTAMP created_at
        TIMESTAMP updated_at
        BIGINT event_id FK
        BIGINT user_id FK
        BIGINT invited_by_user_id FK
        ENUM status
        VARCHAR responsibilities
    }

    event_logs {
        BIGINT id PK
        TIMESTAMP created_at
        TIMESTAMP updated_at
        BIGINT event_id FK
        BIGINT group_id FK
        BIGINT actor_id FK
        BIGINT reverted_log_id FK
        ENUM action_type
        VARCHAR previous_value
        VARCHAR new_value
        VARCHAR notes
    }

    villain_definitions {
        BIGINT id PK
        TIMESTAMP created_at
        TIMESTAMP updated_at
        VARCHAR name
        ENUM type
        ENUM hp_rule_type
        INT hp_rule_value
        ENUM threat_rule_type
        INT threat_rule_value
        BIGINT owner_id FK
    }

    inter_pod_card_definitions {
        BIGINT id PK
        TIMESTAMP created_at
        TIMESTAMP updated_at
        VARCHAR name
        ENUM type
        ENUM card_category
        INT default_hp_max
        INT default_threat_max
        ENUM default_status
        VARCHAR image_path
        VARCHAR image_content_type
        BIGINT owner_id FK
    }

    event_inter_pod_card_definitions {
        BIGINT event_id PK, FK
        BIGINT inter_pod_card_definition_id PK, FK
    }

    event_inter_pod_card {
        BIGINT id PK
        TIMESTAMP created_at
        TIMESTAMP updated_at
        BIGINT event_id FK
        BIGINT pod_id FK
        BIGINT source_group_id FK
        BIGINT inter_pod_card_definition_id FK
        VARCHAR card_name
        ENUM card_category
        INT hp_max
        INT hp_current
        INT threat_max
        INT threat_current
        ENUM status
        VARCHAR image_path
        VARCHAR image_content_type
    }

    event_organizers ||--o{ events : "organiza"
    event_organizers o|--o{ villain_definitions : "posee"
    event_organizers o|--o{ inter_pod_card_definitions : "posee"
    event_organizers ||--o{ invitations : "participa"
    event_organizers o|--o{ invitations : "invita"
    event_organizers o|--o{ event_logs : "actor"

    villain_definitions o|--o{ events : "villano base"

    events ||--o| game_states : "tiene estado"
    events ||--o{ pods : "contiene"
    events ||--o{ player_table : "agrupa"
    events ||--o{ invitations : "coorganiza"
    events ||--o{ event_logs : "registra"
    events ||--o{ event_inter_pod_card : "despliega"
    events ||--o{ event_inter_pod_card_definitions : "habilita"

    pods o|--o{ player_table : "ubica"
    pods ||--o{ event_inter_pod_card : "destino"

    player_table o|--o{ event_logs : "contexto"
    player_table ||--o{ event_inter_pod_card : "origen"

    inter_pod_card_definitions ||--o{ event_inter_pod_card_definitions : "catálogo"
    inter_pod_card_definitions o|--o{ event_inter_pod_card : "instancia"

    event_logs o|--o{ event_logs : "revierte"
```
