package com.okpost.reseaux;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompteSocialRepository extends JpaRepository<CompteSocial, UUID> {
    List<CompteSocial> findByClientIdOrderByNom(UUID clientId);
    List<CompteSocial> findByClientIdAndReseau(UUID clientId, String reseau);
    Optional<CompteSocial> findFirstByClientIdAndReseauAndActifTrue(UUID clientId, String reseau);
    Optional<CompteSocial> findByIdAndClientId(UUID id, UUID clientId);
}
