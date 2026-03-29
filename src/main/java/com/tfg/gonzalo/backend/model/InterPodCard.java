package com.tfg.gonzalo.backend.model;

import com.tfg.gonzalo.backend.model.enums.InterPodCardCategory;
import com.tfg.gonzalo.backend.model.enums.InterPodCardStatus;
import com.tfg.gonzalo.backend.model.enums.InterPodCardType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "inter_pod_cards")
public class InterPodCard extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pod_id", nullable = false)
    private Pod pod;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_group_id", nullable = false)
    private EventGroup sourceGroup;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private InterPodCardType cardName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InterPodCardCategory cardCategory;

    @Column(nullable = false)
    private Integer hpMax;

    @Column(nullable = false)
    private Integer hpCurrent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InterPodCardStatus status = InterPodCardStatus.REVELADA;

    @Column(length = 500)
    private String imagePath;

    @Column(length = 120)
    private String imageContentType;
}