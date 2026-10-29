package com.okpost.abonnement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AbonnementRepository extends JpaRepository<Abonnement, UUID> {
    Optional<Abonnement> findFirstByWorkspaceIdOrderByFinDesc(UUID workspaceId);
}
