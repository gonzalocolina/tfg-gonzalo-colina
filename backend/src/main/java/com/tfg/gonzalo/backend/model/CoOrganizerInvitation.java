package com.tfg.gonzalo.backend.model;

import com.tfg.gonzalo.backend.model.enums.CoOrganizerInvitationStatus;
import com.tfg.gonzalo.backend.model.enums.CoOrganizerResponsibility;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "co_organizer_invitations")
public class CoOrganizerInvitation extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser invitee;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "invited_by_user_id")
    private AppUser invitedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CoOrganizerInvitationStatus status = CoOrganizerInvitationStatus.PENDING;

    @Convert(converter = CoOrganizerResponsibilitiesConverter.class)
    @Column(name = "responsibilities", nullable = false, length = 100)
    private Set<CoOrganizerResponsibility> responsibilities = new LinkedHashSet<>();
}