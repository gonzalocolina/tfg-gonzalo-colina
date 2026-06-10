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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "villain_definitions")
public class VillainDefinition extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VillainDefinitionType type = VillainDefinitionType.CUSTOM;

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

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "owner_id")
    private AppUser owner;
}