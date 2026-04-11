package com.tfg.gonzalo.backend.model;

import com.tfg.gonzalo.backend.model.enums.EventResult;
import com.tfg.gonzalo.backend.model.enums.EventStatus;
import com.tfg.gonzalo.backend.model.enums.VillainDefinitionType;
import com.tfg.gonzalo.backend.model.enums.VillainRuleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "events")
public class Event extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private Integer configuredPlayerCount;

    @Column(nullable = false)
    private Integer timeLimitMinutes = 180;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventStatus status = EventStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private EventResult result;

    @Column(nullable = false, unique = true, length = 20)
    private String eventCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "villain_definition_id")
    private VillainDefinition villainDefinition;

    @Column(length = 120)
    private String villainNameSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private VillainDefinitionType villainTypeSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private VillainRuleType villainHpRuleTypeSnapshot;

    @Column
    private Integer villainHpRuleValueSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private VillainRuleType villainThreatRuleTypeSnapshot;

    @Column
    private Integer villainThreatRuleValueSnapshot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organizer_id", nullable = false)
    private AppUser organizer;

        @ManyToMany(fetch = FetchType.LAZY)
        @JoinTable(
            name = "event_inter_pod_card_definitions",
            joinColumns = @JoinColumn(name = "event_id"),
            inverseJoinColumns = @JoinColumn(name = "inter_pod_card_definition_id")
        )
        private Set<InterPodCardDefinition> interPodCardDefinitions = new LinkedHashSet<>();

    @Column
    private Instant startedAt;

    @Column
    private Instant finishedAt;
}