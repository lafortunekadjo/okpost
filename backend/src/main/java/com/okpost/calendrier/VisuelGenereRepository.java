package com.okpost.calendrier;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** Nombre d'utilisations récentes de chaque photo de la banque : [assetId, nombre]. */
    @Query("select v.assetId, count(v) from VisuelGenere v where v.assetId in :ids and v.createdAt > :depuis group by v.assetId")
    List<Object[]> usagesDepuis(@Param("ids") Collection<UUID> ids, @Param("depuis") Instant depuis);
}
