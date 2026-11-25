-- Où poser le logo en filigrane sur les photos produit : CENTRE (discret), COIN (bas à droite) ou AUCUN
ALTER TABLE client ADD COLUMN position_filigrane VARCHAR(10) NOT NULL DEFAULT 'CENTRE';
