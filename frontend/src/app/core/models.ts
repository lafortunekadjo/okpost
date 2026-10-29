export type Role = 'SUPER_ADMIN' | 'ADMIN' | 'CM';

export interface SessionUtilisateur {
  jetonAcces: string;
  userId: string;
  workspaceId: string;
  nom: string;
  email: string;
  role: Role;
}

export interface Offre { nom: string; description?: string; prix?: string; }

export interface Asset { id: string; type: string; url: string; nomOrigine?: string; legende?: string; }

export interface Client {
  id?: string;
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
}

export interface ClientResume {
  id: string; nom: string; secteur?: string; ville?: string; logoUrl?: string;
  aValider: number; validees: number; aModifier: number;
}

export interface CalendrierResume {
  id: string; debut: string; fin: string; reseaux: string[]; statutGeneration: string; publications: number;
}

export interface TexteCarrousel { visuel: number; titre: string; texte: string; }
export interface BriefVisuel { type?: string; description?: string; promptImage?: string; ratio?: string; textesCarrousel?: TexteCarrousel[]; }
export interface PlanVideo { numero: number; dureeS: number; image?: string; texteEcran?: string; voixOff?: string; son?: string; }
export interface ScenarioVideo { titre?: string; dureeTotaleS?: number; accroche?: string; plans?: PlanVideo[]; materiel?: string[]; }

export interface Retour { decision: string; commentaire?: string; legendeProposee?: string; legendeOrigine?: string; date: string; }

export type StatutPublication = 'IDEE' | 'BROUILLON' | 'A_VALIDER' | 'VALIDEE' | 'A_MODIFIER' | 'PROGRAMMEE' | 'PUBLIEE';

export interface Publication {
  id: string; calendrierId: string; date: string; reseau: string; format: string; pilier?: string; idee: string;
  objectif?: string; tempsFort?: string; legende?: string; hashtags?: string[]; cta?: string;
  brief?: BriefVisuel; scenario?: ScenarioVideo; detailGenere: boolean; statut: StatutPublication; version: number;
  retours?: Retour[];
}

export interface Calendrier {
  id: string; clientId: string; clientNom: string; debut: string; fin: string; reseaux: string[];
  rythme: Record<string, number>; objectifs?: string; tempsForts?: string; langue: string; strategie?: string;
  piliers?: string[]; statutGeneration: 'EN_ATTENTE' | 'GRILLE' | 'DETAILS' | 'TERMINE' | 'ERREUR';
  messageErreur?: string; fournisseurIa?: string; total: number; detaillees: number; publications: Publication[];
}

export interface LienValidation {
  id: string; url: string; expireLe: string; revoque: boolean; publications: number; emailDestinataire?: string; creeLe: string;
}

export interface PortailPublication {
  id: string; date: string; reseau: string; format: string; legende?: string; hashtags?: string[]; cta?: string;
  brief?: BriefVisuel; scenario?: ScenarioVideo; statut: StatutPublication; derniereDecision?: string; dernierCommentaire?: string;
}

export interface Portail {
  clientNom: string; logoUrl?: string; couleur?: string; debut: string; fin: string; expireLe: string;
  total: number; validees: number; aModifier: number; publications: PortailPublication[];
}

export interface Membre { id: string; nom: string; email: string; role: Role; actif: boolean; }

export interface Abonnement {
  formule: string; debut: string; fin: string; statut: string; mode: string;
  maxClients: number; maxMembres: number; calendriersParMois: number; prixMensuelXaf: number;
}

export interface EtatIa { fournisseurParDefaut: string; fournisseurEspace: string; configures: Record<string, boolean>; }

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
