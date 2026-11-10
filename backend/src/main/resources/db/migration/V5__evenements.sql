-- Catalogue de fêtes, journées mondiales et temps forts, proposés à la création d'un calendrier.
-- regle : FIXE (mois, jour), PAQUES (decalage en jours depuis Pâques), NIEME (mois, nieme = 1..4 ou -1 pour le dernier,
--         jour_semaine 1 = lundi … 7 = dimanche, puis decalage), DATE (date_unique, pour les fêtes à date mobile).
-- secteurs / pays : mots-clés séparés par des virgules, sans accents ; vide = tous.

CREATE TABLE evenement (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nom           VARCHAR(200) NOT NULL,
    categorie     VARCHAR(20)  NOT NULL,           -- NATIONAL, RELIGIEUX, COMMERCIAL, MONDIAL, SAISON
    regle         VARCHAR(10)  NOT NULL,
    mois          INT,
    jour          INT,
    nieme         INT,
    jour_semaine  INT,
    decalage      INT NOT NULL DEFAULT 0,
    date_unique   DATE,
    duree_jours   INT NOT NULL DEFAULT 1,
    secteurs      TEXT NOT NULL DEFAULT '',
    pays          TEXT NOT NULL DEFAULT '',
    angle         TEXT,
    approximative BOOLEAN NOT NULL DEFAULT FALSE,
    actif         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE client ADD COLUMN fetes_religieuses BOOLEAN NOT NULL DEFAULT TRUE;

-- ---------- Fêtes nationales et civiles ----------
INSERT INTO evenement (nom, categorie, regle, mois, jour, pays, angle) VALUES
 ('Nouvel An', 'COMMERCIAL', 'FIXE', 1, 1, '', 'Vœux, bilan de l''année, promesses pour l''année qui commence'),
 ('Fête de la Jeunesse', 'NATIONAL', 'FIXE', 2, 11, 'cameroun', 'Jeunes, avenir, talents locaux'),
 ('Fête du Travail', 'NATIONAL', 'FIXE', 5, 1, '', 'Hommage à l''équipe, coulisses, métiers'),
 ('Fête nationale de l''Unité', 'NATIONAL', 'FIXE', 5, 20, 'cameroun', 'Fierté camerounaise, unité, produits locaux'),
 ('Fête de l''Indépendance du Sénégal', 'NATIONAL', 'FIXE', 4, 4, 'senegal', NULL),
 ('Fête de l''Indépendance du Togo', 'NATIONAL', 'FIXE', 4, 27, 'togo', NULL),
 ('Fête de l''Indépendance de la RDC', 'NATIONAL', 'FIXE', 6, 30, 'rdc,democratique', NULL),
 ('Fête de l''Indépendance du Bénin', 'NATIONAL', 'FIXE', 8, 1, 'benin', NULL),
 ('Fête de l''Indépendance de la Côte d''Ivoire', 'NATIONAL', 'FIXE', 8, 7, 'cote d''ivoire', NULL),
 ('Fête de l''Indépendance du Tchad', 'NATIONAL', 'FIXE', 8, 11, 'tchad', NULL),
 ('Fête de l''Indépendance du Congo', 'NATIONAL', 'FIXE', 8, 15, 'brazzaville', NULL),
 ('Fête de l''Indépendance du Gabon', 'NATIONAL', 'FIXE', 8, 17, 'gabon', NULL),
 ('Fête de l''Indépendance du Mali', 'NATIONAL', 'FIXE', 9, 22, 'mali', NULL),
 ('Fête de l''Indépendance de la Guinée', 'NATIONAL', 'FIXE', 10, 2, 'guinee', NULL),
 ('Fête nationale de la Centrafrique', 'NATIONAL', 'FIXE', 12, 1, 'centrafrique', NULL),
 ('Fête nationale du Burkina Faso', 'NATIONAL', 'FIXE', 12, 11, 'burkina', NULL),
 ('Fête de la République du Niger', 'NATIONAL', 'FIXE', 12, 18, 'niger', NULL);

-- ---------- Fêtes religieuses ----------
INSERT INTO evenement (nom, categorie, regle, decalage, angle) VALUES
 ('Vendredi saint', 'RELIGIEUX', 'PAQUES', -2, NULL),
 ('Pâques', 'RELIGIEUX', 'PAQUES', 0, 'Vœux de Pâques, famille, repas de fête'),
 ('Lundi de Pâques', 'RELIGIEUX', 'PAQUES', 1, NULL),
 ('Ascension', 'RELIGIEUX', 'PAQUES', 39, NULL),
 ('Pentecôte', 'RELIGIEUX', 'PAQUES', 49, NULL);
INSERT INTO evenement (nom, categorie, regle, mois, jour, angle) VALUES
 ('Assomption', 'RELIGIEUX', 'FIXE', 8, 15, NULL),
 ('Toussaint', 'RELIGIEUX', 'FIXE', 11, 1, NULL),
 ('Noël', 'RELIGIEUX', 'FIXE', 12, 25, 'Vœux, cadeaux, ambiance de fête');
-- Fêtes musulmanes : dates prévisionnelles (calendrier lunaire, à un jour près), à mettre à jour chaque année.
INSERT INTO evenement (nom, categorie, regle, date_unique, duree_jours, approximative, angle) VALUES
 ('Début du Ramadan', 'RELIGIEUX', 'DATE', DATE '2026-02-18', 30, TRUE, 'Horaires adaptés, offres de rupture du jeûne'),
 ('Aïd el-Fitr (fin du Ramadan)', 'RELIGIEUX', 'DATE', DATE '2026-03-20', 1, TRUE, 'Vœux de fête'),
 ('Tabaski (Aïd el-Kébir)', 'RELIGIEUX', 'DATE', DATE '2026-05-27', 1, TRUE, 'Vœux de fête, partage, famille'),
 ('Mawlid', 'RELIGIEUX', 'DATE', DATE '2026-08-26', 1, TRUE, NULL),
 ('Début du Ramadan', 'RELIGIEUX', 'DATE', DATE '2027-02-08', 30, TRUE, 'Horaires adaptés, offres de rupture du jeûne'),
 ('Aïd el-Fitr (fin du Ramadan)', 'RELIGIEUX', 'DATE', DATE '2027-03-10', 1, TRUE, 'Vœux de fête'),
 ('Tabaski (Aïd el-Kébir)', 'RELIGIEUX', 'DATE', DATE '2027-05-17', 1, TRUE, 'Vœux de fête, partage, famille'),
 ('Mawlid', 'RELIGIEUX', 'DATE', DATE '2027-08-15', 1, TRUE, NULL);

-- ---------- Temps forts commerciaux et saisons ----------
INSERT INTO evenement (nom, categorie, regle, mois, jour, nieme, jour_semaine, decalage, duree_jours, approximative, angle) VALUES
 ('Saint-Valentin', 'COMMERCIAL', 'FIXE', 2, 14, NULL, NULL, 0, 1, FALSE, 'Idées cadeaux, offres pour les couples, amitié'),
 ('Fête des mères', 'COMMERCIAL', 'NIEME', 5, NULL, -1, 7, 0, 1, FALSE, 'Hommage aux mamans, idées cadeaux (dernier dimanche de mai au Cameroun)'),
 ('Fête des pères', 'COMMERCIAL', 'NIEME', 6, NULL, 3, 7, 0, 1, FALSE, 'Hommage aux papas, idées cadeaux'),
 ('Grandes vacances', 'SAISON', 'FIXE', 7, 1, NULL, NULL, 0, 62, TRUE, 'Activités, voyages, programmes pour les enfants'),
 ('Rentrée scolaire', 'SAISON', 'FIXE', 9, 1, NULL, NULL, 0, 21, TRUE, 'Préparatifs, budget, organisation des familles'),
 ('Black Friday', 'COMMERCIAL', 'NIEME', 11, NULL, 4, 4, 1, 1, FALSE, 'Offre limitée, compte à rebours'),
 ('Cyber Monday', 'COMMERCIAL', 'NIEME', 11, NULL, 4, 4, 4, 1, FALSE, NULL),
 ('Fêtes de fin d''année', 'COMMERCIAL', 'FIXE', 12, 15, NULL, NULL, 0, 17, FALSE, 'Cadeaux, commandes avant les fêtes, horaires spéciaux'),
 ('Saint-Sylvestre', 'COMMERCIAL', 'FIXE', 12, 31, NULL, NULL, 0, 1, FALSE, 'Bilan de l''année, remerciements à la communauté');

-- ---------- Journées mondiales (tous secteurs) ----------
INSERT INTO evenement (nom, categorie, regle, mois, jour, secteurs, angle) VALUES
 ('Journée internationale des droits des femmes', 'MONDIAL', 'FIXE', 3, 8, '', 'Femmes de l''équipe, clientes, entrepreneuses ; tenue du 8 mars'),
 ('Journée mondiale de l''environnement', 'MONDIAL', 'FIXE', 6, 5, 'environnement,agro,agri,energie,ferme,recyclage', 'Gestes concrets de l''entreprise');

-- ---------- Santé ----------
INSERT INTO evenement (nom, categorie, regle, mois, jour, duree_jours, secteurs, angle) VALUES
 ('Journée mondiale contre le cancer', 'MONDIAL', 'FIXE', 2, 4, 1, 'sante,hopital,clinique,medic,pharmac,laboratoire,soin', 'Dépistage, prévention'),
 ('Journée mondiale de la tuberculose', 'MONDIAL', 'FIXE', 3, 24, 1, 'sante,hopital,clinique,medic,pharmac,laboratoire', 'Symptômes, dépistage gratuit ou accessible'),
 ('Journée mondiale de la santé', 'MONDIAL', 'FIXE', 4, 7, 1, 'sante,hopital,clinique,medic,pharmac,laboratoire,soin', 'Thème de l''année de l''OMS'),
 ('Journée mondiale de lutte contre le paludisme', 'MONDIAL', 'FIXE', 4, 25, 1, 'sante,hopital,clinique,medic,pharmac,laboratoire', 'Prévention, moustiquaires, signes d''alerte chez l''enfant'),
 ('Journée mondiale de l''hygiène des mains', 'MONDIAL', 'FIXE', 5, 5, 1, 'sante,hopital,clinique,medic,pharmac,laboratoire', NULL),
 ('Journée internationale des infirmières', 'MONDIAL', 'FIXE', 5, 12, 1, 'sante,hopital,clinique,medic,soin', 'Hommage à l''équipe soignante'),
 ('Journée mondiale sans tabac', 'MONDIAL', 'FIXE', 5, 31, 1, 'sante,hopital,clinique,medic,pharmac', NULL),
 ('Journée mondiale du donneur de sang', 'MONDIAL', 'FIXE', 6, 14, 1, 'sante,hopital,clinique,medic,laboratoire', 'Appel au don, où donner'),
 ('Journée mondiale contre l''hépatite', 'MONDIAL', 'FIXE', 7, 28, 1, 'sante,hopital,clinique,medic,pharmac,laboratoire', 'Dépistage, vaccination'),
 ('Semaine mondiale de l''allaitement maternel', 'MONDIAL', 'FIXE', 8, 1, 7, 'sante,hopital,clinique,medic,pharmac,matern,pediatr', NULL),
 ('Journée mondiale Alzheimer', 'MONDIAL', 'FIXE', 9, 21, 1, 'sante,hopital,clinique,medic', NULL),
 ('Journée mondiale du cœur', 'MONDIAL', 'FIXE', 9, 29, 1, 'sante,hopital,clinique,medic,pharmac,sport,fitness', 'Tension, activité physique'),
 ('Octobre rose', 'MONDIAL', 'FIXE', 10, 1, 31, 'sante,hopital,clinique,medic,pharmac,laboratoire,beaute,lingerie', 'Dépistage du cancer du sein, autopalpation'),
 ('Journée mondiale de la santé mentale', 'MONDIAL', 'FIXE', 10, 10, 1, 'sante,hopital,clinique,medic,psych', NULL),
 ('Movember', 'MONDIAL', 'FIXE', 11, 1, 30, 'sante,hopital,clinique,medic,coiffure,barbier', 'Santé masculine, cancer de la prostate'),
 ('Journée mondiale du diabète', 'MONDIAL', 'FIXE', 11, 14, 1, 'sante,hopital,clinique,medic,pharmac,laboratoire,nutrition', 'Dépistage de la glycémie, alimentation'),
 ('Journée mondiale de la prématurité', 'MONDIAL', 'FIXE', 11, 17, 1, 'sante,hopital,clinique,medic,matern,pediatr', NULL),
 ('Journée mondiale de lutte contre le sida', 'MONDIAL', 'FIXE', 12, 1, 1, 'sante,hopital,clinique,medic,pharmac,laboratoire', 'Dépistage, prévention, sans jugement');

-- ---------- Agroalimentaire, restauration, agriculture ----------
INSERT INTO evenement (nom, categorie, regle, mois, jour, secteurs, angle) VALUES
 ('Journée mondiale de l''eau', 'MONDIAL', 'FIXE', 3, 22, 'agro,agri,aliment,boisson,eau,environnement,ferme', NULL),
 ('Journée mondiale du lait', 'MONDIAL', 'FIXE', 6, 1, 'agro,agri,aliment,lait,elevage,nutrition', NULL),
 ('Journée mondiale de la sécurité sanitaire des aliments', 'MONDIAL', 'FIXE', 6, 7, 'agro,agri,aliment,restaur,cuisine,traiteur,boulanger,food,hygiene,qualite', 'Bonnes pratiques d''hygiène, contrôle qualité'),
 ('Journée internationale de sensibilisation aux pertes et gaspillages de nourriture', 'MONDIAL', 'FIXE', 9, 29, 'agro,agri,aliment,restaur,cuisine,traiteur,food', NULL),
 ('Journée internationale de la femme rurale', 'MONDIAL', 'FIXE', 10, 15, 'agro,agri,ferme,elevage,cooperative', NULL),
 ('Journée mondiale de l''alimentation', 'MONDIAL', 'FIXE', 10, 16, 'agro,agri,aliment,restaur,cuisine,traiteur,nutrition,food,ferme', 'Thème de l''année de la FAO, savoir-faire local'),
 ('Journée mondiale des sols', 'MONDIAL', 'FIXE', 12, 5, 'agro,agri,ferme,environnement', NULL);

-- ---------- Éducation, formation, jeunesse ----------
INSERT INTO evenement (nom, categorie, regle, mois, jour, secteurs, angle) VALUES
 ('Journée internationale de l''éducation', 'MONDIAL', 'FIXE', 1, 24, 'formation,ecole,educ,academ,universit,enseign,learning,cours', NULL),
 ('Journée mondiale des compétences des jeunes', 'MONDIAL', 'FIXE', 7, 15, 'formation,ecole,educ,academ,universit,emploi,recrut', NULL),
 ('Journée internationale de la jeunesse', 'MONDIAL', 'FIXE', 8, 12, 'formation,ecole,educ,academ,universit,sport,emploi', NULL),
 ('Journée internationale de l''alphabétisation', 'MONDIAL', 'FIXE', 9, 8, 'formation,ecole,educ,academ,enseign', NULL),
 ('Journée mondiale des enseignants', 'MONDIAL', 'FIXE', 10, 5, 'formation,ecole,educ,academ,universit,enseign', 'Hommage aux formateurs');

-- ---------- Sport, numérique, transport ----------
INSERT INTO evenement (nom, categorie, regle, mois, jour, secteurs, angle) VALUES
 ('Journée internationale du sport au service du développement et de la paix', 'MONDIAL', 'FIXE', 4, 6, 'sport,foot,club,fitness,gym,basket', NULL),
 ('Journée mondiale des télécommunications et de la société de l''information', 'MONDIAL', 'FIXE', 5, 17, 'tech,numerique,informatique,digital,logiciel,telecom', NULL),
 ('Journée olympique', 'MONDIAL', 'FIXE', 6, 23, 'sport,foot,club,fitness,gym,basket', NULL),
 ('Journée mondiale du transport durable', 'MONDIAL', 'FIXE', 11, 26, 'transport,fret,logistique,transit,livraison', NULL);

-- Peu suivi en Afrique centrale : désactivé, réactivable depuis Réglages.
UPDATE evenement SET actif = FALSE WHERE nom = 'Cyber Monday';
