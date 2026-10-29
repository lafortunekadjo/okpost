package com.okpost.validation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RetourClientRepository extends JpaRepository<RetourClient, UUID> {
    List<RetourClient> findByPublicationIdInOrderByCreatedAtDesc(Collection<UUID> publicationIds);
    List<RetourClient> findByPublicationIdOrderByCreatedAtDesc(UUID publicationId);
}
