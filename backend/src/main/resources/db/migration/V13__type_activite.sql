-- Services, produits (boutique) ou les deux : adapte les consignes de l'IA et les visuels
ALTER TABLE client ADD COLUMN type_activite VARCHAR(12) NOT NULL DEFAULT 'SERVICES';
