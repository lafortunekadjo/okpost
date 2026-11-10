-- Analyse de la Page avant le calendrier, et relevés d'abonnés pour les bilans.

ALTER TABLE calendrier ADD COLUMN analyser_page BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE calendrier ADD COLUMN analyse_page TEXT;

CREATE TABLE releve_page (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    compte_id   UUID NOT NULL REFERENCES compte_social(id) ON DELETE CASCADE,
    jour        DATE NOT NULL,
    abonnes     INT,
    UNIQUE (compte_id, jour)
);
