-- Grille de notation de la qualité des calendriers générés (tests des modèles d'IA).

ALTER TABLE calendrier ALTER COLUMN fournisseur_ia TYPE VARCHAR(150);
ALTER TABLE calendrier ADD COLUMN note_equilibre INT;
ALTER TABLE calendrier ADD COLUMN avis_global TEXT;

CREATE TABLE evaluation_publication (
    publication_id  UUID PRIMARY KEY REFERENCES publication(id) ON DELETE CASCADE,
    calendrier_id   UUID NOT NULL REFERENCES calendrier(id) ON DELETE CASCADE,
    specificite     INT,
    exactitude      INT,
    contexte        INT,
    faisabilite     INT,
    pret            INT,
    commentaire     TEXT,
    evaluateur_id   UUID,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_evaluation_calendrier ON evaluation_publication(calendrier_id);
