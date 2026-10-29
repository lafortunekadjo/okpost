package com.okpost.validation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LienValidationRepository extends JpaRepository<LienValidation, UUID> {
    Optional<LienValidation> findByJeton(String jeton);
    Optional<LienValidation> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
    List<LienValidation> findByCalendrierIdOrderByCreatedAtDesc(UUID calendrierId);
}
