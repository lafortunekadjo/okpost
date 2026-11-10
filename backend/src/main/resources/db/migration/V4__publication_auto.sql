-- Lot 6 : comptes sociaux reliés et publication automatique

CREATE TABLE compte_social (
    id                  UUID PRIMARY KEY,
    workspace_id        UUID NOT NULL REFERENCES workspace(id),
    client_id           UUID NOT NULL REFERENCES client(id) ON DELETE CASCADE,
    reseau              VARCHAR(20) NOT NULL,
    compte_externe_id   VARCHAR(100) NOT NULL,
    nom                 VARCHAR(255),
    jeton_chiffre       TEXT NOT NULL,
    actif               BOOLEAN NOT NULL DEFAULT FALSE,
    expire_le           TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_compte_social_client ON compte_social(client_id, reseau);

ALTER TABLE client ADD COLUMN auto_publication BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE client ADD COLUMN heure_publication VARCHAR(5) NOT NULL DEFAULT '18:00';
ALTER TABLE client ADD COLUMN fuseau VARCHAR(50) NOT NULL DEFAULT 'Africa/Douala';

ALTER TABLE publication ADD COLUMN publication_prevue TIMESTAMPTZ;
ALTER TABLE publication ADD COLUMN publiee_le TIMESTAMPTZ;
ALTER TABLE publication ADD COLUMN post_externe_id VARCHAR(100);
ALTER TABLE publication ADD COLUMN lien_post VARCHAR(500);
ALTER TABLE publication ADD COLUMN erreur_publication TEXT;
ALTER TABLE publication ADD COLUMN tentatives INT NOT NULL DEFAULT 0;
CREATE INDEX idx_publication_programmee ON publication(statut, publication_prevue);
