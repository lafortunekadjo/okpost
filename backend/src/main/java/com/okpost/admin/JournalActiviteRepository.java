package com.okpost.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface JournalActiviteRepository extends JpaRepository<JournalActivite, UUID> {

    @Modifying
    @Query("delete from JournalActivite j where j.createdAt < :limite")
    int supprimerAvant(Instant limite);
}
