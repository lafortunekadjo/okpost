-- Formules commerciales modifiables par le super-admin (prix, limites). Les quatre codes restent ceux de l'application.
CREATE TABLE formule_config (
    code                 VARCHAR(20) PRIMARY KEY,
    nom                  VARCHAR(40) NOT NULL,
    prix_mensuel_xaf     BIGINT NOT NULL,
    max_clients          INT NOT NULL,
    max_membres          INT NOT NULL,
    calendriers_par_mois INT NOT NULL,
    images_par_mois      INT NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO formule_config (code, nom, prix_mensuel_xaf, max_clients, max_membres, calendriers_par_mois, images_par_mois) VALUES
    ('ESSAI',  'Essai',        0, 1, 1,  1,   5),
    ('SOLO',   'Solo',      7500, 3, 1,  3,  30),
    ('PRO',    'Pro',      15000, 10, 2, 10, 100),
    ('AGENCE', 'Agence',   35000, 30, 5, 30, 300);
