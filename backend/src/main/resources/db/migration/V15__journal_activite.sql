-- Journal d'activité : qui a fait quoi (comptes, clients, calendriers, validations, publications).
-- Pas d'adresse IP ni de contenu : seulement l'action, son auteur et un repère. Conservation limitée (purge automatique).
CREATE TABLE journal_activite (
    id             UUID PRIMARY KEY,
    workspace_id   UUID,
    utilisateur_id UUID,
    auteur         VARCHAR(190),
    action         VARCHAR(40) NOT NULL,
    cible          VARCHAR(190),
    detail         VARCHAR(500),
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_journal_activite_date ON journal_activite(created_at DESC);
CREATE INDEX idx_journal_activite_workspace ON journal_activite(workspace_id, created_at DESC);
