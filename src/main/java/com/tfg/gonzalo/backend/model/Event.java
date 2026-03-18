package com.tfg.gonzalo.backend.model;

import com.tfg.gonzalo.backend.model.enums.EventResult;
import com.tfg.gonzalo.backend.model.enums.EventStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

    @Column
    private Instant startedAt;

    @Column
    private Instant finishedAt;
}