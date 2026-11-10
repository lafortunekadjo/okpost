package com.okpost.evenement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EvenementRepository extends JpaRepository<Evenement, UUID> {
    List<Evenement> findByActifTrue();
    List<Evenement> findAllByOrderByCategorieAscMoisAscJourAsc();
}
