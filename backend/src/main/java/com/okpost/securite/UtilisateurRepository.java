package com.okpost.securite;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, UUID> {
    Optional<Utilisateur> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<Utilisateur> findByWorkspaceIdOrderByNom(UUID workspaceId);
    Optional<Utilisateur> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
