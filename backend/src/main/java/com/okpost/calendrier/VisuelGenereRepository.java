package com.okpost.calendrier;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VisuelGenereRepository extends JpaRepository<VisuelGenere, UUID> {
    List<VisuelGenere> findByPublicationIdInOrderByCreatedAtDesc(Collection<UUID> publicationIds);
    Optional<VisuelGenere> findByIdAndPublicationId(UUID id, UUID publicationId);
    Optional<VisuelGenere> findByChemin(String chemin);
    long countByWorkspaceIdAndSourceAndCreatedAtAfter(UUID workspaceId, VisuelGenere.Source source, Instant depuis);
}
