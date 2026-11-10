package com.okpost.analyse;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface RelevePageRepository extends JpaRepository<RelevePage, UUID> {
    Optional<RelevePage> findByCompteIdAndJour(UUID compteId, LocalDate jour);
    Optional<RelevePage> findFirstByCompteIdAndJourLessThanEqualOrderByJourDesc(UUID compteId, LocalDate jour);
}
