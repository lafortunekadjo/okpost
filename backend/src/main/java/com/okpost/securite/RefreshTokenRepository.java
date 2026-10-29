package com.okpost.securite;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByJetonHash(String jetonHash);

    @Modifying
    @Query("delete from RefreshToken r where r.expireLe < :limite")
    int supprimerExpires(Instant limite);

    @Modifying
    @Query("delete from RefreshToken r where r.userId = :userId")
    int supprimerPourUtilisateur(UUID userId);
}
