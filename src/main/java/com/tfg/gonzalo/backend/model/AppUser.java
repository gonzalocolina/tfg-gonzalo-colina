package com.tfg.gonzalo.backend.model;

import com.tfg.gonzalo.backend.model.enums.GlobalRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "app_users")
public class AppUser extends BaseEntity {

    @Column(nullable = false, unique = true, length = 60)
    private String username;

    @Column(nullable = false, length = 120)
    private String displayName;

    @Column(unique = true, length = 120)
    private String email;

    @Column(length = 120)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private GlobalRole role;

    @Column(nullable = false)
    private boolean enabled = true;
}