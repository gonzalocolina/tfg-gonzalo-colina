package com.tfg.gonzalo.backend.model;

import com.tfg.gonzalo.backend.model.enums.EventResult;
import com.tfg.gonzalo.backend.model.enums.GameFinishCause;
import com.tfg.gonzalo.backend.model.enums.TimerStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
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
@Table(name = "game_states")
public class GameState extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, unique = true)
    private Event event;

    @Column(nullable = false)
    private Integer villainHpMax;

    @Column(nullable = false)
    private Integer villainHpCurrent;

    @Column(nullable = false)
    private Integer schemeThreatTarget;

    @Column(nullable = false)
    private Integer schemeThreatCurrent = 0;

    @Column(nullable = false)
    private Integer timerTotalSeconds;

    @Column(nullable = false)
    private Integer timerRemainingSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TimerStatus timerStatus = TimerStatus.NOT_CONFIGURED;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private EventResult result;

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
    private Instant finishedAt;
}