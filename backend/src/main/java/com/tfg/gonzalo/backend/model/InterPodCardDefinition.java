package com.tfg.gonzalo.backend.model;

import com.tfg.gonzalo.backend.model.enums.InterPodCardCategory;
import com.tfg.gonzalo.backend.model.enums.InterPodCardDefinitionType;
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
@Table(name = "inter_pod_card_definitions")
public class InterPodCardDefinition extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InterPodCardDefinitionType type = InterPodCardDefinitionType.CUSTOM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InterPodCardCategory cardCategory;

    @Column(nullable = false)
    private Integer defaultHpMax;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InterPodCardStatus defaultStatus = InterPodCardStatus.REVELADA;

    @Column(length = 500)
    private String imagePath;

    @Column(length = 120)
    private String imageContentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private AppUser owner;
}
