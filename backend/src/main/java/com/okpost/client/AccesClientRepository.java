package com.okpost.client;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccesClientRepository extends JpaRepository<AccesClient, UUID> {

    List<AccesClient> findByClientIdOrderByCreatedAt(UUID clientId);

    List<AccesClient> findByUserIdOrderByCreatedAt(UUID userId);

    boolean existsByClientIdAndUserId(UUID clientId, UUID userId);

    Optional<AccesClient> findByClientIdAndUserId(UUID clientId, UUID userId);

    Optional<AccesClient> findByIdAndClientId(UUID id, UUID clientId);

    long countByClientId(UUID clientId);
}
