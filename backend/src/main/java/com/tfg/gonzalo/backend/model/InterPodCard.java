package com.tfg.gonzalo.backend.model;

import com.tfg.gonzalo.backend.model.enums.InterPodCardCategory;
import com.tfg.gonzalo.backend.model.enums.InterPodCardStatus;
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
@Table(name = "inter_pod_card_instances")
public class InterPodCard extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "pod_id", nullable = false)
    private Pod pod;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "source_group_id", nullable = false)
    private EventGroup sourceGroup;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "inter_pod_card_definition_id")
    private InterPodCardDefinition interPodCardDefinition;

    @Column(nullable = false, length = 120)
    private String cardName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InterPodCardCategory cardCategory;

    @Column(name = "metric_max", nullable = false)
    private Integer metricMax = 0;

    @Column(name = "metric_current", nullable = false)
    private Integer metricCurrent = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InterPodCardStatus status = InterPodCardStatus.REVELADA;

    @Column(length = 500)
    private String imagePath;

    @Column(length = 120)
    private String imageContentType;
}