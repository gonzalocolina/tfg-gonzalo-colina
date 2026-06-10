package com.tfg.gonzalo.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users")
public class AppUser extends BaseEntity {

    @Column(nullable = false, unique = true, length = 60)
    private String username;

    @Column(unique = true, length = 120)
    private String email;

    @Column(length = 120)
    private String passwordHash;
}