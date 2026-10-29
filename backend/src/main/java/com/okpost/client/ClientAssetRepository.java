package com.okpost.client;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientAssetRepository extends JpaRepository<ClientAsset, UUID> {
    List<ClientAsset> findByClientIdOrderByCreatedAt(UUID clientId);
    Optional<ClientAsset> findByIdAndClientId(UUID id, UUID clientId);
    Optional<ClientAsset> findByChemin(String chemin);
}
