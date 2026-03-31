package com.tfg.gonzalo.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "event_groups")
public class EventGroup extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pod_id")
    private Pod pod;

    @Column(nullable = false, length = 40)
    private String identifier;

    @Column(nullable = false)
    private Integer playerCount;

    @Column(nullable = false, unique = true, length = 16)
    private String representativeCode;

    @Column(nullable = false, unique = true, length = 16)
    private String observerCode;
}