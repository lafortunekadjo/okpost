-- Liens de partage en lecture seule (en plus des liens de validation)
ALTER TABLE lien_validation ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'VALIDATION';

-- Médias téléversés par le CM (photo ou vidéo), à côté des images générées
ALTER TABLE visuel_genere ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'GENERE';
ALTER TABLE visuel_genere ADD COLUMN type_media VARCHAR(10) NOT NULL DEFAULT 'IMAGE';
ALTER TABLE visuel_genere ADD COLUMN nom_origine VARCHAR(255);
ALTER TABLE visuel_genere ADD COLUMN taille BIGINT;
ALTER TABLE visuel_genere ALTER COLUMN prompt DROP NOT NULL;
ALTER TABLE visuel_genere ALTER COLUMN fournisseur DROP NOT NULL;
