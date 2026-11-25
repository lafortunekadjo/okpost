-- Collaborateur invité sur un seul client d'un autre espace : il travaille dans l'espace du propriétaire, limité à ce client.
CREATE TABLE acces_client (
    id            UUID PRIMARY KEY,
    workspace_id  UUID NOT NULL REFERENCES workspace(id),
    client_id     UUID NOT NULL REFERENCES client(id) ON DELETE CASCADE,
    user_id       UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    invite_par    UUID,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_acces_client UNIQUE (client_id, user_id)
);
CREATE INDEX idx_acces_client_user ON acces_client(user_id);
