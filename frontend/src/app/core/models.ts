export type Role = 'SUPER_ADMIN' | 'ADMIN' | 'CM' | 'COLLAB';

export interface SessionUtilisateur {
  jetonAcces: string;
  userId: string;
  workspaceId: string;
  nom: string;
  email: string;
  role: Role;
  /** Renseigné pour une session limitée à un client partagé (rôle COLLAB). */
  clientId?: string;
}

export interface Collaborateur { id: string; nom: string; email: string; depuis: string; }

export interface ClientPartage { clientId: string; clientNom: string; espace: string; depuis: string; }

export interface Offre {
  nom: string; description?: string; prix?: string; variantes?: string; photos?: string[];
  categorie?: string; tailles?: string[]; couleurs?: string[]; disponible?: boolean;
}

/** Produit proposé par la lecture d'un site ou de publications : à relire avant d'être ajouté. */
export interface ProduitPropose {
  nom: string; description?: string; prix?: string; categorie?: string; tailles?: string[]; couleurs?: string[];
  disponible?: boolean; imageUrl?: string;
}

export interface ResultatCatalogueWeb { produits: ProduitPropose[]; source: string; avertissements: string[]; }

export interface ResultatCatalogue { produits: Offre[]; avertissements: string[]; photosRattachees: number; }

export type TypeActivite = 'SERVICES' | 'PRODUITS' | 'MIXTE';

/** Piste de la bibliothèque de musiques du serveur. */
export interface Musique { id: string; nom: string; taille: number; }

/** Ce qui demande l'attention du CM : publication échouée, sans heure, en retard ou non programmée. */
export interface Alerte {
  type: 'ECHEC' | 'SANS_HEURE' | 'EN_RETARD' | 'NON_PROGRAMMEE' | 'JETON' | string;
  niveau: 'ERREUR' | 'ATTENTION' | string;
  publicationId?: string | null; clientId: string; clientNom: string; reseau: string; date: string; message: string;
}

/** Résultat de « mettre sur d'autres réseaux » : copies créées, réseaux ignorés (avec la raison), textes réécrits par l'IA. */
export interface ResultatDuplication { creees: string[]; ignorees: string[]; adaptees: number; }

export interface Asset { id: string; type: string; url: string; nomOrigine?: string; legende?: string;
  description?: string; tags?: string[]; analyse?: 'AUCUNE' | 'EN_COURS' | 'OK' | 'ECHEC' | string; }

/** Fiche proposée à partir du site web d'un client (rien n'est enregistré : l'utilisateur relit puis enregistre). */
export interface FicheProposee {
  nom?: string | null; secteur?: string | null; description?: string | null; slogan?: string | null; anneeCreation?: number | null;
  ville?: string | null; pays?: string | null; zone?: string | null; adresse?: string | null; horaires?: string | null;
  langue?: 'fr' | 'en' | null; ton?: string | null; tutoiement?: boolean | null;
  offres: Offre[]; cibles: string[]; couleurs: string[]; domaine?: string | null; reseaux: Record<string, string>;
  whatsapp?: string | null; logos: string[]; photos: string[]; sources: string[]; avertissements: string[];
}

export interface Client {
  id?: string;
  typeActivite?: TypeActivite;
  positionFiligrane?: 'CENTRE' | 'COIN' | 'AUCUN';
  nom: string;
  secteur?: string;
  description?: string;
  slogan?: string;
  anneeCreation?: number | null;
  ville?: string;
  pays?: string;
  zone?: string;
  adresse?: string;
  horaires?: string;
  langue: 'fr' | 'en';
  ton?: string;
  tutoiement: boolean;
  motsAEviter?: string;
  exemples?: string;
  offres: Offre[];
  cibles: string[];
  concurrents: string[];
  couleurs: string[];
  polices?: string;
  domaine?: string;
  reseaux: Record<string, string>;
  whatsapp?: string;
  cmId?: string;
  assets?: Asset[];
  updatedAt?: string;
  autoPublication?: boolean;
  heurePublication?: string;
  fetesReligieuses?: boolean;
}

export interface CompteSocial { id: string; reseau: string; nom: string; compteExterneId: string; actif: boolean; erreur?: string | null; }

export interface ClientResume {
  id: string; nom: string; secteur?: string; ville?: string; logoUrl?: string;
  aValider: number; validees: number; aModifier: number;
  prochaine?: string | null; derniere?: string | null;
}

export interface CalendrierResume {
  id: string; debut: string; fin: string; reseaux: string[]; statutGeneration: string; publications: number; archive?: boolean;
}

export interface TexteCarrousel { visuel: number; titre: string; texte: string; }
export interface BriefVisuel { type?: string; description?: string; promptImage?: string; ratio?: string; textesCarrousel?: TexteCarrousel[]; accroche?: string;
  gabarit?: string; chiffre?: string; texte?: string; cta?: string; produit?: string; produits?: string[]; }

export interface LienCommande { url: string; message: string; produit?: string; }
export interface PlanVideo { numero: number; dureeS: number; image?: string; texteEcran?: string; voixOff?: string; son?: string; }
export interface ScenarioVideo { titre?: string; dureeTotaleS?: number; accroche?: string; plans?: PlanVideo[]; materiel?: string[]; }

export interface Retour { decision: string; commentaire?: string; legendeProposee?: string; legendeOrigine?: string; date: string; par?: string; }

export interface AdminCompte {
  id: string; nom: string; creeLe: string; emailAdmin?: string; membres: number; clients: number; calendriers: number;
  publications: number; publiees: number; imagesMois: number; appelsIaMois: number; formule?: string; statutAbonnement?: string;
  finAbonnement?: string; derniereConnexion?: string;
}

export interface AdminTotaux {
  comptes: number; clients: number; calendriers: number; publications: number; publieesMois: number;
  appelsIaMois: number; echecsIaMois: number; imagesMois: number; echecsPublication7j: number;
}

export interface AdminLigne { date: string; action: string; libelle: string; espace?: string; espaceId?: string; auteur?: string; cible?: string; detail?: string; }

export interface AdminJournal { total: number; page: number; taille: number; lignes: AdminLigne[]; actions: Record<string, string>; }

export interface ContactValidation { id: string; nom: string; actif: boolean; derniereUtilisation?: string; creeLe?: string; }

export interface Visuel {
  id: string; url: string; prompt?: string; ratio?: string; modele?: string; date: string;
  type?: 'IMAGE' | 'VIDEO' | string; source?: 'GENERE' | 'TELEVERSE' | 'MONTAGE' | 'BANQUE' | string; nomOrigine?: string;
  diapositive?: number | null;
}

export interface PartageCalendrier {
  clientNom: string; logoUrl?: string; couleurs?: string[]; debut: string; fin: string; strategie?: string;
  piliers?: string[]; objectifs?: string; tempsForts?: string; reseaux?: string[]; expireLe: string;
  publications: {
    id: string; date: string; reseau: string; format: string; pilier?: string; idee: string; objectif?: string;
    legende?: string; hashtags?: string[]; cta?: string; brief?: BriefVisuel; scenario?: ScenarioVideo;
    medias?: { url: string; type: string }[];
  }[];
}

export type StatutPublication = 'IDEE' | 'BROUILLON' | 'A_VALIDER' | 'VALIDEE' | 'A_MODIFIER' | 'PROGRAMMEE' | 'PUBLIEE';

export interface Publication {
  id: string; calendrierId: string; date: string; reseau: string; format: string; pilier?: string; idee: string;
  objectif?: string; tempsFort?: string; legende?: string; hashtags?: string[]; cta?: string;
  brief?: BriefVisuel; scenario?: ScenarioVideo; detailGenere: boolean; statut: StatutPublication; version: number;
  retours?: Retour[];
  visuels?: Visuel[];
  publicationPrevue?: string;
  /** Heure locale prévue (HH:mm) ; vide = heure par défaut du client. */
  heure?: string | null;
  /** Pourquoi cette heure a été choisie. */
  heureMotif?: string | null;
  publieeLe?: string;
  lienPost?: string;
  erreurPublication?: string;
  modeCarrousel?: 'VIDEO' | 'PHOTOS' | null;
}

export interface Calendrier {
  id: string; clientId: string; clientNom: string; debut: string; fin: string; reseaux: string[];
  rythme: Record<string, number>; objectifs?: string; tempsForts?: string; langue: string; strategie?: string;
  piliers?: string[]; statutGeneration: 'EN_ATTENTE' | 'GRILLE' | 'DETAILS' | 'TERMINE' | 'ERREUR';
  messageErreur?: string; fournisseurIa?: string; total: number; detaillees: number; publications: Publication[]; archive?: boolean;
  analyserPage?: boolean; analysePage?: string;
}

export interface LienValidation {
  id: string; url: string; expireLe: string; revoque: boolean; publications: number; emailDestinataire?: string; creeLe: string;
  type?: 'VALIDATION' | 'CONSULTATION' | string;
}

export interface PortailPublication {
  id: string; date: string; reseau: string; format: string; legende?: string; hashtags?: string[]; cta?: string;
  brief?: BriefVisuel; scenario?: ScenarioVideo; statut: StatutPublication; derniereDecision?: string; dernierCommentaire?: string;
  visuelUrl?: string;
  visuelType?: string;
}

export interface Portail {
  clientNom: string; logoUrl?: string; couleur?: string; debut: string; fin: string; expireLe: string;
  total: number; validees: number; aModifier: number; publications: PortailPublication[];
  codeRequis?: boolean; validateur?: string;
}

export interface Membre { id: string; nom: string; email: string; role: Role; actif: boolean; }

export interface Abonnement {
  formule: string; debut: string; fin: string; statut: string; mode: string;
  maxClients: number; maxMembres: number; calendriersParMois: number; imagesParMois: number; prixMensuelXaf: number;
}

export interface EtatIa { fournisseurParDefaut: string; fournisseurEspace: string; configures: Record<string, boolean>; images: Record<string, boolean>; imageParDefaut?: string; }

export interface Consommation { fournisseur: string; modele: string; appels: number; echecs: number; jetonsEntree: number; jetonsSortie: number; }

export const RESEAUX = ['FACEBOOK', 'INSTAGRAM', 'TIKTOK', 'LINKEDIN'] as const;

export const LIBELLES_STATUT: Record<StatutPublication, string> = {
  IDEE: 'Idée', BROUILLON: 'Brouillon', A_VALIDER: 'À valider', VALIDEE: 'Validée',
  A_MODIFIER: 'À modifier', PROGRAMMEE: 'Programmée', PUBLIEE: 'Publiée',
};

export const LIBELLES_RESEAU: Record<string, string> = {
  FACEBOOK: 'Facebook', INSTAGRAM: 'Instagram', TIKTOK: 'TikTok', LINKEDIN: 'LinkedIn',
};

export const LIBELLES_FORMAT: Record<string, string> = {
  IMAGE: 'Image', CARROUSEL: 'Carrousel', VIDEO: 'Vidéo', TEXTE: 'Texte', STORY: 'Story',
};

export interface PropositionEvenement {
  id: string; nom: string; debut: string; fin?: string; categorie: string;
  raison: string; angle?: string; approximative: boolean;
}

export interface TempsFortSuggere { nom: string; date?: string | null; angle?: string; }

export interface EvenementCatalogue {
  id: string; nom: string; categorie: string; regle: string; mois?: number; jour?: number; nieme?: number;
  jourSemaine?: number; decalage: number; dateUnique?: string; dureeJours: number; secteurs: string; pays: string;
  angle?: string; approximative: boolean; actif: boolean;
}

export interface NotesPublication {
  publicationId?: string; specificite?: number | null; exactitude?: number | null; contexte?: number | null;
  faisabilite?: number | null; pret?: number | null; commentaire?: string | null;
}

export interface MoyennesEvaluation {
  specificite?: number; exactitude?: number; contexte?: number; faisabilite?: number; pret?: number;
  globale?: number; partEnvoyables?: number; evaluees: number;
}

export interface EvaluationCalendrier {
  calendrierId: string; modele: string; total: number; noteEquilibre?: number; avisGlobal?: string;
  moyennes: MoyennesEvaluation; notes: NotesPublication[];
}

export interface LigneSynthese { modele: string; calendriers: number; moyennes: MoyennesEvaluation; equilibre?: number; }

export interface StatistiquesPage {
  publications: number; reactions: number; commentaires: number; partages: number; vues?: number | null;
  engagement: number; engagementMoyen: number; vuesMoyennes?: number | null;
  parType: Record<string, number>; engagementParType: Record<string, number>;
  engagementParJour: Record<string, number>; engagementParCreneau: Record<string, number>;
}

export interface PostResume {
  date?: string; extrait: string; type: string; reactions: number; commentaires: number; partages: number;
  vues?: number | null; lien?: string;
}

export interface BilanPage {
  page: string; debut: string; fin: string; debutPrecedent: string; finPrecedent: string;
  actuelle: StatistiquesPage; precedente: StatistiquesPage; abonnes?: number | null; abonnesDebut?: number | null;
  jourReleveDebut?: string | null; publieesParOkPost: number; meilleurs: PostResume[]; moinsBons: PostResume[];
  vuesDisponibles: boolean; commentairesDisponibles: boolean; remarque?: string | null;
}

export interface Recommandations { constat?: string; recommandations: string[]; }
