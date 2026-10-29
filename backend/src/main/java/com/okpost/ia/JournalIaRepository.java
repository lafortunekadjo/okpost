package com.okpost.ia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface JournalIaRepository extends JpaRepository<JournalIa, UUID> {

    interface Consommation {
        String getFournisseur();
        String getModele();
        long getAppels();
        long getEchecs();
        Long getJetonsEntree();
        Long getJetonsSortie();
    }

    @Query("""
            select j.fournisseur as fournisseur, j.modele as modele, count(j) as appels,
                   sum(case when j.succes = false then 1 else 0 end) as echecs,
                   sum(j.jetonsEntree) as jetonsEntree, sum(j.jetonsSortie) as jetonsSortie
            from JournalIa j
            where j.workspaceId = :workspaceId and j.createdAt >= :depuis
            group by j.fournisseur, j.modele
            """)
    List<Consommation> consommation(UUID workspaceId, Instant depuis);
}
