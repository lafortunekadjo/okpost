-- Vérification quotidienne des jetons : dernier contrôle et erreur éventuelle (compte à reconnecter).
ALTER TABLE compte_social ADD COLUMN erreur VARCHAR(400);
ALTER TABLE compte_social ADD COLUMN verifie_le TIMESTAMPTZ;
