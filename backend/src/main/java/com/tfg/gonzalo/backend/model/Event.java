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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organizer_id", nullable = false)
    private AppUser organizer;

    @OneToOne(mappedBy = "event", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private EventVillain villain;

    @Column(nullable = false)
    private Integer villainHpMax = 0;

    @Column(nullable = false)
    private Integer villainHpCurrent = 0;

    @Column(nullable = false)
    private Integer schemeThreatTarget = 0;

    @Column(nullable = false)
    private Integer schemeThreatCurrent = 0;

    @Column(nullable = false)
    private Integer timerTotalSeconds = 0;

    @Column(nullable = false)
    private Integer timerRemainingSeconds = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TimerStatus timerStatus = TimerStatus.NOT_CONFIGURED;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private GameFinishCause finishCause;

    @Column(nullable = false)
    private Integer totalReportedVillainDamage = 0;

    @Column(nullable = false)
    private boolean villainHalfHpNotified = false;

    @Column(nullable = false)
    private boolean villainQuarterHpNotified = false;

    @Column(nullable = false)
    private boolean threatHalfNotified = false;

    @Column(nullable = false)
    private boolean threatThreeQuarterNotified = false;

    @Column(nullable = false)
    private boolean timerThirtyMinutesNotified = false;

    @Column(nullable = false)
    private boolean timerFifteenMinutesNotified = false;

    @Column(nullable = false)
    private boolean timerFiveMinutesNotified = false;

    @Column
    private Instant timerStartedAt;

    @Column
    private Instant timerPausedAt;

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
}