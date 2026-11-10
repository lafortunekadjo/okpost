-- Carrousels : un visuel par diapositive, montage en vidéo défilante, choix du mode de publication.

ALTER TABLE visuel_genere ADD COLUMN diapositive INT;
ALTER TABLE publication ADD COLUMN mode_carrousel VARCHAR(10);   -- VIDEO (vidéo défilante) ou PHOTOS (plusieurs photos)
