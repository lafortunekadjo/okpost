import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import {
  Abonnement, Alerte, Asset, FicheProposee, Musique, ResultatDuplication, Calendrier, CalendrierResume, Client, ClientResume, Consommation, EtatIa,
  AdminCompte, AdminJournal, AdminTotaux, LienCommande, ResultatCatalogue, ResultatCatalogueWeb, CompteSocial, ContactValidation, LienValidation, Membre, PartageCalendrier, Portail, Publication, Visuel,
  PropositionEvenement, TempsFortSuggere, EvenementCatalogue, EvaluationCalendrier, LigneSynthese, NotesPublication,
  BilanPage, Recommandations,
} from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);
  private base = '/api/v1';

  clients() { return this.http.get<ClientResume[]>(`${this.base}/clients`); }
  client(id: string) { return this.http.get<Client>(`${this.base}/clients/${id}`); }
  creerClient(c: Client) { return this.http.post<Client>(`${this.base}/clients`, c); }
  modifierClient(id: string, c: Client) { return this.http.put<Client>(`${this.base}/clients/${id}`, c); }
  clientsArchives() { return this.http.get<ClientResume[]>(`${this.base}/clients/archives`); }
  restaurerClient(id: string) { return this.http.post<void>(`${this.base}/clients/${id}/restaurer`, {}); }
  supprimerClientDefinitivement(id: string) { return this.http.delete<void>(`${this.base}/clients/${id}/definitif`); }
  archiverCalendrier(id: string) { return this.http.post<void>(`${this.base}/calendriers/${id}/archiver`, {}); }
  restaurerCalendrier(id: string) { return this.http.post<void>(`${this.base}/calendriers/${id}/restaurer`, {}); }
  supprimerPublication(id: string) { return this.http.delete<void>(`${this.base}/publications/${id}`); }
  archiverClient(id: string) { return this.http.delete<void>(`${this.base}/clients/${id}`); }
  ajouterFichier(clientId: string, type: string, fichier: File, legende?: string) {
    const f = new FormData();
    f.append('type', type);
    f.append('fichier', fichier);
    if (legende) f.append('legende', legende);
    return this.http.post<Asset>(`${this.base}/clients/${clientId}/assets`, f);
  }
  modifierFichier(clientId: string, assetId: string, patch: { legende?: string; description?: string; tags?: string[] }) {
    return this.http.patch<Asset>(`${this.base}/clients/${clientId}/assets/${assetId}`, patch);
  }
  analyserFichier(clientId: string, assetId: string) {
    return this.http.post<Asset>(`${this.base}/clients/${clientId}/assets/${assetId}/analyser`, {});
  }
  supprimerFichier(clientId: string, assetId: string) {
    return this.http.delete<void>(`${this.base}/clients/${clientId}/assets/${assetId}`);
  }

  calendriers(clientId: string) { return this.http.get<CalendrierResume[]>(`${this.base}/clients/${clientId}/calendriers`); }
  creerCalendrier(clientId: string, d: unknown) { return this.http.post<Calendrier>(`${this.base}/clients/${clientId}/calendriers`, d); }
  calendrier(id: string) { return this.http.get<Calendrier>(`${this.base}/calendriers/${id}`); }
  relancer(id: string) { return this.http.post<void>(`${this.base}/calendriers/${id}/relancer`, {}); }
  supprimerCalendrier(id: string) { return this.http.delete<void>(`${this.base}/calendriers/${id}`); }

  publication(id: string) { return this.http.get<Publication>(`${this.base}/publications/${id}`); }
  modifierPublication(id: string, patch: Partial<Publication>) {
    return this.http.patch<Publication>(`${this.base}/publications/${id}`, patch);
  }
  genererImage(id: string, prompt?: string, ratio?: string, habiller = true, accroche?: string, moteur?: string,
               diapositive?: number, affiche?: { gabarit?: string; chiffre?: string; texte?: string; cta?: string; photo?: string }) {
    return this.http.post<Visuel>(`${this.base}/publications/${id}/visuels`,
      { prompt: prompt || null, ratio: ratio || null, habiller, accroche: accroche ?? null, moteur: moteur || null,
        diapositive: diapositive ?? null, gabarit: affiche?.gabarit || null, chiffre: affiche?.chiffre || null,
        texte: affiche?.texte || null, cta: affiche?.cta || null, photo: affiche?.photo || null });
  }
  /** seulementImages : ne cherche que le logo et les photos (sans IA, rapide). */
  analyserSite(url: string, seulementImages = false) {
    return this.http.post<FicheProposee>(`${this.base}/clients/analyser-site`, { url, seulementImages });
  }
  /** Propose une fiche à partir des pages Facebook / Instagram reliées (client sans site web). */
  analyserReseaux(clientId: string) {
    return this.http.post<FicheProposee>(`${this.base}/clients/${clientId}/analyser-reseaux`, {});
  }
  /** Importe dans la fiche une image du site du client : logo, ou photo pour la banque d'images. */
  importerImage(clientId: string, url: string, type: 'PHOTO' | 'LOGO_COULEUR') {
    return this.http.post<Asset>(`${this.base}/clients/${clientId}/assets/depuis-url`, { url, type });
  }
  dupliquerPublication(id: string, reseaux: string[], adapter: boolean) {
    return this.http.post<ResultatDuplication>(`${this.base}/publications/${id}/dupliquer`, { reseaux, adapter });
  }
  dupliquerSelection(calendrierId: string, ids: string[], reseaux: string[], adapter: boolean) {
    return this.http.post<ResultatDuplication>(`${this.base}/calendriers/${calendrierId}/dupliquer`, { ids, reseaux, adapter });
  }
  surveillance() { return this.http.get<Alerte[]>(`${this.base}/surveillance`); }
  musiques() { return this.http.get<Musique[]>(`${this.base}/musiques`); }
  ajouterMusique(fichier: File) {
    const f = new FormData();
    f.append('fichier', fichier);
    return this.http.post<Musique>(`${this.base}/musiques`, f);
  }
  supprimerMusique(id: string) { return this.http.delete<void>(`${this.base}/musiques/${id}`); }
  ecouterMusique(id: string) { return this.http.get(`${this.base}/musiques/${id}/ecoute`, { responseType: 'blob' }); }
  monterCarrousel(id: string, secondes: number, transition: string, musique?: File | null, musiqueId?: string | null) {
    const f = new FormData();
    f.append('secondes', String(secondes));
    f.append('transition', transition);
    if (musique) f.append('musique', musique);
    if (musiqueId) f.append('musiqueId', musiqueId);
    return this.http.post<Visuel>(`${this.base}/publications/${id}/montage`, f);
  }
  modeCarrousel(id: string, mode: 'VIDEO' | 'PHOTOS') {
    return this.http.put<Publication>(`${this.base}/publications/${id}/mode-carrousel`, { mode });
  }
  supprimerImage(id: string, visuelId: string) {
    return this.http.delete<void>(`${this.base}/publications/${id}/visuels/${visuelId}`);
  }
  regenerer(id: string, champ: 'legende' | 'cta' | 'tout', consigne: string) {
    return this.http.post<Publication>(`${this.base}/publications/${id}/regenerer`, { champ, consigne });
  }

  liens(calendrierId: string) { return this.http.get<LienValidation[]>(`${this.base}/calendriers/${calendrierId}/liens-validation`); }
  creerLien(calendrierId: string, publications: string[], email?: string) {
    return this.http.post<LienValidation>(`${this.base}/calendriers/${calendrierId}/liens-validation`, { publications, email: email || null });
  }
  envoyerLien(lienId: string, email: string) {
    return this.http.post<LienValidation>(`${this.base}/liens-validation/${lienId}/envoyer-email`, { email });
  }
  revoquerLien(lienId: string) { return this.http.delete<void>(`${this.base}/liens-validation/${lienId}`); }

  comptesSociaux(clientId: string) {
    return this.http.get<{ comptes: CompteSocial[]; connexionFacebookDisponible: boolean; instagramDisponible?: boolean }>(`${this.base}/clients/${clientId}/comptes-sociaux`);
  }
  urlConnexionFacebook(clientId: string) {
    return this.http.get<{ url: string }>(`${this.base}/clients/${clientId}/comptes-sociaux/facebook/connexion`);
  }
  relierFacebookJeton(clientId: string, jeton: string) {
    return this.http.post<CompteSocial[]>(`${this.base}/clients/${clientId}/comptes-sociaux/facebook/jeton`, { jeton });
  }
  activerCompte(clientId: string, compteId: string) {
    return this.http.post<CompteSocial[]>(`${this.base}/clients/${clientId}/comptes-sociaux/${compteId}/activer`, {});
  }
  supprimerCompte(clientId: string, compteId: string) {
    return this.http.delete<void>(`${this.base}/clients/${clientId}/comptes-sociaux/${compteId}`);
  }
  programmer(id: string, quand?: string) {
    return this.http.post<Publication>(`${this.base}/publications/${id}/programmer`, quand ? { quand } : {});
  }
  programmerValidees(calendrierId: string) {
    return this.http.post<{ programmees: number; ignorees: string[] }>(`${this.base}/calendriers/${calendrierId}/programmer-validees`, {});
  }
  annulerProgrammation(id: string) { return this.http.post<Publication>(`${this.base}/publications/${id}/annuler-programmation`, {}); }
  publierMaintenant(id: string) { return this.http.post<Publication>(`${this.base}/publications/${id}/publier-maintenant`, {}); }
  partager(calendrierId: string, email?: string) {
    return this.http.post<LienValidation>(`${this.base}/calendriers/${calendrierId}/partage`, { email: email || null });
  }
  partagePublic(jeton: string) { return this.http.get<PartageCalendrier>(`${this.base}/public/partage/${jeton}`); }
  televerserMedia(id: string, fichier: File, diapositive?: number) {
    const f = new FormData();
    f.append('fichier', fichier);
    if (diapositive) f.append('diapositive', String(diapositive));
    return this.http.post<Visuel>(`${this.base}/publications/${id}/medias`, f, { reportProgress: true, observe: 'events' });
  }
  /** Le code personnel du client passe dans un en-tête, jamais dans l'adresse (elle se retrouve dans les journaux). */
  private enteteCode(code?: string | null) { return code ? { headers: new HttpHeaders({ 'X-Code-Validation': code }) } : {}; }
  portail(jeton: string, code?: string | null) { return this.http.get<Portail>(`${this.base}/public/validation/${jeton}`, this.enteteCode(code)); }
  decider(jeton: string, pubId: string, decision: 'OK' | 'A_MODIFIER', commentaire?: string, code?: string | null) {
    return this.http.post<void>(`${this.base}/public/validation/${jeton}/publications/${pubId}`, { decision, commentaire }, this.enteteCode(code));
  }
  modifierLegendePortail(jeton: string, pubId: string, legende: string, commentaire?: string, code?: string | null) {
    return this.http.put<void>(`${this.base}/public/validation/${jeton}/publications/${pubId}/legende`, { legende, commentaire }, this.enteteCode(code));
  }

  /** Lit un catalogue CSV : renvoie les produits à relire, rien n'est enregistré. */
  importerCatalogue(clientId: string, fichier: File) {
    const f = new FormData();
    f.append('fichier', fichier);
    return this.http.post<ResultatCatalogue>(`${this.base}/clients/${clientId}/catalogue/importer`, f);
  }
  /** Lien wa.me avec un message de commande déjà écrit, pour une publication (son produit) ou un produit du catalogue. */
  lienCommandePublication(id: string) { return this.http.get<LienCommande>(`${this.base}/publications/${id}/lien-commande`); }
  lienCommandeProduit(clientId: string, produit: string) {
    return this.http.get<LienCommande>(`${this.base}/clients/${clientId}/lien-commande`, { params: { produit } });
  }
  /** Corrige, reformule ou raccourcit un texte de la fiche : renvoie une proposition, rien n'est enregistré. */
  reformuler(d: { texte: string; sujet?: string; mode: string; champ: 'produit' | 'activite'; clientId?: string }) {
    return this.http.post<{ texte: string }>(`${this.base}/reformuler`, d);
  }
  /** Décrit brièvement un produit d'après une photo de la banque d'images : proposition à ajuster, rien n'est enregistré. */
  decrireProduit(clientId: string, photoId: string, nom?: string) {
    return this.http.post<{ texte: string }>(`${this.base}/clients/${clientId}/decrire-produit`, { photoId, nom });
  }
  /** Lit le site du client (Shopify, WooCommerce, pages produit, ou texte par l'IA) : produits à relire, rien n'est enregistré. */
  catalogueDepuisSite(clientId: string, url: string) {
    return this.http.post<ResultatCatalogueWeb>(`${this.base}/clients/${clientId}/catalogue/depuis-site`, { url });
  }
  /** Repère les produits cités dans les publications Facebook et Instagram reliées. */
  catalogueDepuisReseaux(clientId: string) {
    return this.http.post<ResultatCatalogueWeb>(`${this.base}/clients/${clientId}/catalogue/depuis-reseaux`, {});
  }
  /** Crée une diapositive par photo du produit mis en avant (logo en filigrane, sans texte) : 10 au plus. */
  carrouselProduit(publicationId: string) {
    return this.http.post<Publication>(`${this.base}/publications/${publicationId}/carrousel-produit`, {});
  }
  adminComptes() { return this.http.get<{ totaux: AdminTotaux; comptes: AdminCompte[] }>(`${this.base}/admin/comptes`); }
  adminJournal(f: { espace?: string; action?: string; page?: number }) {
    const params: Record<string, string> = { page: String(f.page ?? 0) };
    if (f.espace) params['espace'] = f.espace;
    if (f.action) params['action'] = f.action;
    return this.http.get<AdminJournal>(`${this.base}/admin/journal`, { params });
  }

  contactsValidation(clientId: string) { return this.http.get<ContactValidation[]>(`${this.base}/clients/${clientId}/contacts-validation`); }
  ajouterContactValidation(clientId: string, nom: string) {
    return this.http.post<{ contact: ContactValidation; code: string }>(`${this.base}/clients/${clientId}/contacts-validation`, { nom });
  }
  renouvelerContactValidation(clientId: string, id: string) {
    return this.http.post<{ contact: ContactValidation; code: string }>(`${this.base}/clients/${clientId}/contacts-validation/${id}/nouveau-code`, {});
  }
  supprimerContactValidation(clientId: string, id: string) { return this.http.delete<void>(`${this.base}/clients/${clientId}/contacts-validation/${id}`); }

  membres() { return this.http.get<Membre[]>(`${this.base}/membres`); }
  inviter(m: { nom: string; email: string; motDePasseProvisoire: string }) { return this.http.post<Membre>(`${this.base}/membres`, m); }
  retirerMembre(id: string) { return this.http.delete<void>(`${this.base}/membres/${id}`); }
  abonnement() { return this.http.get<Abonnement>(`${this.base}/abonnements/courant`); }
  etatIa() { return this.http.get<EtatIa>(`${this.base}/reglages/ia`); }
  evenementsProposes(clientId: string, debut: string, fin: string) {
    return this.http.get<PropositionEvenement[]>(`${this.base}/clients/${clientId}/evenements`, { params: { debut, fin } });
  }
  suggestionsTempsForts(clientId: string, debut: string, fin: string, deja: string[]) {
    return this.http.post<TempsFortSuggere[]>(`${this.base}/clients/${clientId}/evenements/suggestions`, { debut, fin, deja });
  }
  catalogueEvenements() { return this.http.get<EvenementCatalogue[]>(`${this.base}/admin/evenements`); }
  ajouterEvenement(e: unknown) { return this.http.post<EvenementCatalogue>(`${this.base}/admin/evenements`, e); }
  activerEvenement(id: string, actif: boolean) {
    return this.http.put<EvenementCatalogue>(`${this.base}/admin/evenements/${id}/actif`, {}, { params: { actif } });
  }
  supprimerEvenement(id: string) { return this.http.delete<void>(`${this.base}/admin/evenements/${id}`); }
  evaluation(calendrierId: string) { return this.http.get<EvaluationCalendrier>(`${this.base}/calendriers/${calendrierId}/evaluation`); }
  noter(publicationId: string, n: NotesPublication) {
    return this.http.put<NotesPublication>(`${this.base}/publications/${publicationId}/evaluation`, n);
  }
  avisCalendrier(calendrierId: string, noteEquilibre: number | null, avisGlobal: string) {
    return this.http.put<EvaluationCalendrier>(`${this.base}/calendriers/${calendrierId}/evaluation`, { noteEquilibre, avisGlobal });
  }
  syntheseEvaluations() { return this.http.get<LigneSynthese[]>(`${this.base}/evaluations/synthese`); }
  etatPage(clientId: string) { return this.http.get<{ pageReliee: boolean }>(`${this.base}/clients/${clientId}/page`); }
  apercuAnalysePage(clientId: string) { return this.http.get<{ texte: string }>(`${this.base}/clients/${clientId}/analyse-page`); }
  bilan(clientId: string, debut: string, fin: string) {
    return this.http.get<BilanPage>(`${this.base}/clients/${clientId}/bilan`, { params: { debut, fin } });
  }
  recommandationsBilan(clientId: string, debut: string, fin: string) {
    return this.http.post<Recommandations>(`${this.base}/clients/${clientId}/bilan/recommandations`, { debut, fin });
  }
  changerIa(fournisseur: string) { return this.http.put<EtatIa>(`${this.base}/admin/parametres/ia`, { fournisseur }); }
  consommation() { return this.http.get<Consommation[]>(`${this.base}/reglages/consommation`); }
}

/** Message d'erreur lisible à partir d'une réponse problem+json. */
export function messageErreur(e: unknown): string {
  if (e instanceof HttpErrorResponse) {
    if (e.status === 0) return 'Serveur injoignable. Vérifie ta connexion.';
    return e.error?.detail ?? e.error?.message ?? `Erreur ${e.status}`;
  }
  return 'Une erreur est survenue.';
}
