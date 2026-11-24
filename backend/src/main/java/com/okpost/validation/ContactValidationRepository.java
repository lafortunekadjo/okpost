package com.okpost.validation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContactValidationRepository extends JpaRepository<ContactValidation, UUID> {

    List<ContactValidation> findByClientIdOrderByNom(UUID clientId);

    List<ContactValidation> findByClientIdAndActifTrue(UUID clientId);

    Optional<ContactValidation> findByIdAndClientId(UUID id, UUID clientId);
}
