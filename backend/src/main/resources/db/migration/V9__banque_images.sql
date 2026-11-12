-- Banque d'images : chaque photo d'un client est décrite (à la main ou par l'IA) pour être retrouvée selon le sujet d'une publication.
ALTER TABLE client_asset ADD COLUMN description TEXT;
ALTER TABLE client_asset ADD COLUMN tags JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE client_asset ADD COLUMN analyse_statut VARCHAR(10) NOT NULL DEFAULT 'AUCUNE';

-- Photo de la banque utilisée par un visuel (pour varier les photos d'une publication à l'autre).
ALTER TABLE visuel_genere ADD COLUMN asset_id UUID REFERENCES client_asset(id) ON DELETE SET NULL;
CREATE INDEX idx_visuel_asset ON visuel_genere(asset_id);
