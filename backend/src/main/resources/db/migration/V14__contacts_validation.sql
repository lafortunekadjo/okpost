-- Personnes autorisées à valider pour un client : chacune a son code, pour savoir qui a validé quoi.
CREATE TABLE contact_validation (
    id            UUID PRIMARY KEY,
    workspace_id  UUID NOT NULL,
    client_id     UUID NOT NULL REFERENCES client(id) ON DELETE CASCADE,
    nom           VARCHAR(150) NOT NULL,
    code_hash     VARCHAR(100) NOT NULL,
    actif         BOOLEAN NOT NULL DEFAULT TRUE,
    derniere_utilisation TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_contact_validation_client ON contact_validation(client_id);

-- Qui a donné chaque retour (nom conservé même si la personne est ensuite supprimée).
ALTER TABLE retour_client ADD COLUMN contact_id UUID;
ALTER TABLE retour_client ADD COLUMN nom_validateur VARCHAR(150);
