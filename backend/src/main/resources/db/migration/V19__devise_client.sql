-- Devise des prix du client : ajoutée sur les affiches et dans les textes quand un prix n'en porte pas
ALTER TABLE client ADD COLUMN devise VARCHAR(12) NOT NULL DEFAULT 'FCFA';
