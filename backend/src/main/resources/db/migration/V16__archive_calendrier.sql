-- Calendrier archivé : masqué des listes et des échéances, mais conservé (historique, bilans)
ALTER TABLE calendrier ADD COLUMN archive BOOLEAN NOT NULL DEFAULT FALSE;
