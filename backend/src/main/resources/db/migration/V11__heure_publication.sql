-- Heure de publication propre à chaque publication (proposée par l'IA, modifiable) ; vide = heure par défaut du client.
ALTER TABLE publication ADD COLUMN heure_prevue VARCHAR(5);
