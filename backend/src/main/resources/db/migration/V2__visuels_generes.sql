-- Images générées par IA à partir du prompt d'une publication
CREATE TABLE visuel_genere (
    id               UUID PRIMARY KEY,
    workspace_id     UUID NOT NULL REFERENCES workspace(id),
    publication_id   UUID NOT NULL REFERENCES publication(id) ON DELETE CASCADE,
    chemin           VARCHAR(255) NOT NULL UNIQUE,
    mime             VARCHAR(50) NOT NULL,
    prompt           TEXT NOT NULL,
    ratio            VARCHAR(10),
    fournisseur      VARCHAR(30) NOT NULL,
    modele           VARCHAR(100),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_visuel_publication ON visuel_genere(publication_id);
CREATE INDEX idx_visuel_workspace_date ON visuel_genere(workspace_id, created_at);
