package com.tfg.gonzalo.backend.model;

import com.tfg.gonzalo.backend.model.enums.GameFinishCause;
import com.tfg.gonzalo.backend.model.enums.EventResult;
import com.tfg.gonzalo.backend.model.enums.EventStatus;
import com.tfg.gonzalo.backend.model.enums.TimerStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
    private Integer timeLimitMinutes = 180;

    @Column(nullable = false, unique = true, length = 20)
    private String eventCode;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "organizer_id", nullable = false)
    private AppUser organizer;

    @OneToOne(mappedBy = "event", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    private EventVillain villain;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "selected_inter_pod_card_definitions",
            joinColumns = @JoinColumn(name = "event_id"),
            inverseJoinColumns = @JoinColumn(name = "inter_pod_card_definition_id")
    )
    private List<InterPodCardDefinition> selectedInterPodCardDefinitions = new ArrayList<>();

    @Column(name = "villain_damage_taken", nullable = false)
    private Integer villainDamageTaken = 0;

    @Column(nullable = false)
    private Integer schemeThreatCurrent = 0;

    @Column(nullable = false)
    private Integer totalReportedVillainDamage = 0;

    @Column(nullable = false)
    private Integer timerRemainingSeconds = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TimerStatus timerStatus = TimerStatus.NOT_CONFIGURED;

    @Column
    private Instant timerStartedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private GameFinishCause finishCause;

    @Column
    private Instant startedAt;

    @Column
    private Instant finishedAt;

    public void setVillain(EventVillain villain) {
        this.villain = villain;
        if (villain != null && villain.getEvent() != this) {
            villain.setEvent(this);
        }
    }

    /**
     * Estado derivado del ciclo de vida: no se persiste, se deduce de los timestamps.
     */
    @Transient
    public EventStatus getStatus() {
        if (finishedAt != null) {
            return EventStatus.FINISHED;
        }
        if (startedAt != null) {
            return EventStatus.IN_PROGRESS;
        }
        return EventStatus.DRAFT;
    }

    /**
     * Resultado derivado 1:1 de la causa de finalización.
     */
    @Transient
    public EventResult getResult() {
        if (finishedAt == null || finishCause == null) {
            return null;
        }
        return switch (finishCause) {
            case VILLAIN_HP_ZERO -> EventResult.PLAYERS_VICTORY;
            case THREAT_TARGET_REACHED -> EventResult.THREAT_DEFEAT;
            case TIMER_EXPIRED -> EventResult.TIMEOUT_DEFEAT;
        };
    }

    @Transient
    public int getTimerTotalSeconds() {
        return timeLimitMinutes == null ? 0 : Math.max(0, timeLimitMinutes) * 60;
    }
}
