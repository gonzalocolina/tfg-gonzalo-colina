package com.tfg.gonzalo.backend.model;

import com.tfg.gonzalo.backend.model.enums.VillainDefinitionType;
import com.tfg.gonzalo.backend.model.enums.VillainRuleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "event_villain")
public class EventVillain extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, unique = true)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "villain_definition_id")
    private VillainDefinition villainDefinition;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VillainDefinitionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VillainRuleType hpRuleType;

    @Column(nullable = false)
    private Integer hpRuleValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VillainRuleType threatRuleType;

    @Column(nullable = false)
    private Integer threatRuleValue;

    public void setEvent(Event event) {
        this.event = event;
        if (event != null && event.getVillain() != this) {
            event.setVillain(this);
        }
    }
}