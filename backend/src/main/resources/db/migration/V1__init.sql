-- OkPost : schéma initial (MVP)

CREATE TABLE workspace (
    id                UUID PRIMARY KEY,
    nom               VARCHAR(150) NOT NULL,
    fournisseur_ia    VARCHAR(20),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE app_user (
    id                  UUID PRIMARY KEY,
    workspace_id        UUID NOT NULL REFERENCES workspace(id),
    email               VARCHAR(190) NOT NULL UNIQUE,
    mot_de_passe_hash   VARCHAR(100) NOT NULL,
    nom                 VARCHAR(150) NOT NULL,
    role                VARCHAR(20) NOT NULL,
    actif               BOOLEAN NOT NULL DEFAULT TRUE,
    tentatives_echouees INT NOT NULL DEFAULT 0,
    bloque_jusqu        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_user_workspace ON app_user(workspace_id);

CREATE TABLE refresh_token (
    id           UUID PRIMARY KEY,
    user_id      UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    jeton_hash   VARCHAR(100) NOT NULL UNIQUE,
    expire_le    TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE client (
    id               UUID PRIMARY KEY,
    workspace_id     UUID NOT NULL REFERENCES workspace(id),
    cm_id            UUID REFERENCES app_user(id),
    nom              VARCHAR(150) NOT NULL,
    secteur          VARCHAR(150),
    description      TEXT,
    slogan           VARCHAR(255),
    annee_creation   INT,
    ville            VARCHAR(100),
    pays             VARCHAR(100),
    zone             VARCHAR(255),
    adresse          VARCHAR(255),
    horaires         VARCHAR(255),
    langue           VARCHAR(5) NOT NULL DEFAULT 'fr',
    ton              VARCHAR(255),
    tutoiement       BOOLEAN NOT NULL DEFAULT FALSE,
    mots_a_eviter    TEXT,
    exemples         TEXT,
    offres           JSONB,
    cibles           JSONB,
    concurrents      JSONB,
    couleurs         JSONB,
    polices          VARCHAR(255),
    domaine          VARCHAR(255),
    reseaux          JSONB,
    whatsapp         VARCHAR(50),
    archive          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_client_workspace ON client(workspace_id);

CREATE TABLE client_asset (
    id            UUID PRIMARY KEY,
    client_id     UUID NOT NULL REFERENCES client(id) ON DELETE CASCADE,
    type          VARCHAR(20) NOT NULL,
    chemin        VARCHAR(255) NOT NULL,
    nom_origine   VARCHAR(255),
    mime          VARCHAR(100) NOT NULL,
    taille        BIGINT NOT NULL,
    legende       VARCHAR(255),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_asset_client ON client_asset(client_id);

CREATE TABLE calendrier (
    id                  UUID PRIMARY KEY,
    workspace_id        UUID NOT NULL REFERENCES workspace(id),
    client_id           UUID NOT NULL REFERENCES client(id),
    periode_debut       DATE NOT NULL,
    periode_fin         DATE NOT NULL,
    reseaux             JSONB NOT NULL,
    rythme              JSONB,
    objectifs           TEXT,
    temps_forts         TEXT,
    langue              VARCHAR(5) NOT NULL DEFAULT 'fr',
    strategie           TEXT,
    piliers             JSONB,
    statut_generation   VARCHAR(20) NOT NULL,
    message_erreur      TEXT,
    fournisseur_ia      VARCHAR(20),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_calendrier_client ON calendrier(client_id);

CREATE TABLE publication (
    id               UUID PRIMARY KEY,
    workspace_id     UUID NOT NULL REFERENCES workspace(id),
    calendrier_id    UUID NOT NULL REFERENCES calendrier(id) ON DELETE CASCADE,
    date_prevue      DATE NOT NULL,
    reseau           VARCHAR(20) NOT NULL,
    format           VARCHAR(20) NOT NULL,
    pilier           VARCHAR(100),
    idee             TEXT NOT NULL,
    objectif         VARCHAR(50),
    temps_fort       VARCHAR(255),
    legende          TEXT,
    hashtags         JSONB,
    cta              TEXT,
    brief            JSONB,
    scenario         JSONB,
    detail_genere    BOOLEAN NOT NULL DEFAULT FALSE,
    statut           VARCHAR(20) NOT NULL,
    version          INT NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_publication_calendrier ON publication(calendrier_id);

CREATE TABLE lien_validation (
    id                   UUID PRIMARY KEY,
    workspace_id         UUID NOT NULL REFERENCES workspace(id),
    calendrier_id        UUID NOT NULL REFERENCES calendrier(id) ON DELETE CASCADE,
    jeton                VARCHAR(64) NOT NULL UNIQUE,
    expire_le            TIMESTAMPTZ NOT NULL,
    revoque              BOOLEAN NOT NULL DEFAULT FALSE,
    publications         JSONB NOT NULL,
    email_destinataire   VARCHAR(190),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE retour_client (
    id                 UUID PRIMARY KEY,
    publication_id     UUID NOT NULL REFERENCES publication(id) ON DELETE CASCADE,
    lien_id            UUID REFERENCES lien_validation(id) ON DELETE SET NULL,
    decision           VARCHAR(20) NOT NULL,
    commentaire        TEXT,
    legende_proposee   TEXT,
    legende_origine    TEXT,
    ip_hash            VARCHAR(100),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_retour_publication ON retour_client(publication_id);

CREATE TABLE journal_ia (
    id              UUID PRIMARY KEY,
    workspace_id    UUID REFERENCES workspace(id),
    fournisseur     VARCHAR(20) NOT NULL,
    modele          VARCHAR(100),
    operation       VARCHAR(50) NOT NULL,
    jetons_entree   INT,
    jetons_sortie   INT,
    duree_ms        BIGINT NOT NULL,
    succes          BOOLEAN NOT NULL,
    erreur          TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_journal_workspace_date ON journal_ia(workspace_id, created_at);

CREATE TABLE abonnement (
    id             UUID PRIMARY KEY,
    workspace_id   UUID NOT NULL REFERENCES workspace(id),
    formule        VARCHAR(20) NOT NULL,
    debut          DATE NOT NULL,
    fin            DATE NOT NULL,
    statut         VARCHAR(20) NOT NULL,
    mode           VARCHAR(20) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_abonnement_workspace ON abonnement(workspace_id);

CREATE TABLE paiement (
    id                      UUID PRIMARY KEY,
    abonnement_id           UUID NOT NULL REFERENCES abonnement(id),
    montant                 BIGINT NOT NULL,
    devise                  VARCHAR(5) NOT NULL DEFAULT 'XAF',
    prestataire             VARCHAR(20) NOT NULL,
    reference_transaction   VARCHAR(100),
    numero_recu             VARCHAR(50),
    saisi_par               UUID REFERENCES app_user(id),
    statut                  VARCHAR(20) NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE parametre_plateforme (
    cle          VARCHAR(100) PRIMARY KEY,
    valeur       VARCHAR(500) NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
