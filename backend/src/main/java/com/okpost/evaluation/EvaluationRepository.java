package com.okpost.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface EvaluationRepository extends JpaRepository<EvaluationPublication, UUID> {
    List<EvaluationPublication> findByCalendrierId(UUID calendrierId);
    List<EvaluationPublication> findByCalendrierIdIn(Collection<UUID> calendrierIds);
}
