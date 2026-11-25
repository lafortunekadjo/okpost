package com.okpost.calendrier;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PublicationRepository extends JpaRepository<Publication, UUID> {

    List<Publication> findByCalendrierIdOrderByDatePrevueAscReseauAsc(UUID calendrierId);

    Optional<Publication> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<Publication> findByWorkspaceIdAndStatut(UUID workspaceId, StatutPublication statut);

    List<Publication> findByIdInAndCalendrierId(Collection<UUID> ids, UUID calendrierId);

    long countByCalendrierIdAndDetailGenereTrue(UUID calendrierId);

    long countByCalendrierId(UUID calendrierId);

    List<Publication> findTop20ByStatutAndPublicationPrevueBeforeAndTentativesLessThanOrderByPublicationPrevue(
            StatutPublication statut, java.time.Instant limite, int tentativesMax);

    long countByCalendrierIdAndDatePrevueBetween(UUID calendrierId, LocalDate debut, LocalDate fin);

    interface CompteStatut {
        StatutPublication getStatut();
        long getNombre();
    }

    @Query("""
            select p.statut as statut, count(p) as nombre
            from Publication p, Calendrier c
            where p.calendrierId = c.id and c.clientId = :clientId and c.archive = false
              and p.datePrevue between :debut and :fin
            group by p.statut
            """)
    List<CompteStatut> compterParStatut(UUID clientId, LocalDate debut, LocalDate fin);

    @Query("select count(p) from Publication p, Calendrier c where p.calendrierId = c.id and c.clientId = :clientId "
            + "and p.publieeLe >= :debut and p.publieeLe < :fin")
    long compterPublieesParOkPost(UUID clientId, java.time.Instant debut, java.time.Instant fin);

    /** Date de la prochaine publication prévue (programmée ou validée) d'un client. */
    @Query("select min(p.datePrevue) from Publication p, Calendrier c where p.calendrierId = c.id and c.clientId = :clientId and c.archive = false "
            + "and p.statut in (com.okpost.calendrier.StatutPublication.PROGRAMMEE, com.okpost.calendrier.StatutPublication.VALIDEE) "
            + "and p.datePrevue >= :aujourdhui")
    LocalDate prochainePublication(UUID clientId, LocalDate aujourdhui);

    /** Moment de la dernière publication réellement publiée d'un client. */
    @Query("select max(p.publieeLe) from Publication p, Calendrier c where p.calendrierId = c.id and c.clientId = :clientId "
            + "and p.statut = com.okpost.calendrier.StatutPublication.PUBLIEE")
    java.time.Instant dernierePublication(UUID clientId);
}
