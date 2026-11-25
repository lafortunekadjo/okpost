package com.okpost.client;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {
    List<Client> findByWorkspaceIdAndArchiveFalseOrderByNom(UUID workspaceId);
    List<Client> findByWorkspaceIdAndCmIdAndArchiveFalseOrderByNom(UUID workspaceId, UUID cmId);
    Optional<Client> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
    long countByWorkspaceIdAndArchiveFalse(UUID workspaceId);
    List<Client> findByWorkspaceIdAndArchiveTrueOrderByNom(UUID workspaceId);
    List<Client> findByWorkspaceIdAndCmIdAndArchiveTrueOrderByNom(UUID workspaceId, UUID cmId);
}
