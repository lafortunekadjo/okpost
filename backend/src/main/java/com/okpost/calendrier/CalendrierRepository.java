package com.okpost.calendrier;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CalendrierRepository extends JpaRepository<Calendrier, UUID> {
    List<Calendrier> findByClientIdAndWorkspaceIdOrderByPeriodeDebutDesc(UUID clientId, UUID workspaceId);
    Optional<Calendrier> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
    long countByWorkspaceIdAndCreatedAtAfter(UUID workspaceId, Instant depuis);
}
