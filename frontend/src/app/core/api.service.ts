import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import {
  Abonnement, Asset, Calendrier, CalendrierResume, Client, ClientResume, Consommation, EtatIa,
  LienValidation, Membre, Portail, Publication,
} from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);
  private base = '/api/v1';

  clients() { return this.http.get<ClientResume[]>(`${this.base}/clients`); }
  client(id: string) { return this.http.get<Client>(`${this.base}/clients/${id}`); }
  creerClient(c: Client) { return this.http.post<Client>(`${this.base}/clients`, c); }
  modifierClient(id: string, c: Client) { return this.http.put<Client>(`${this.base}/clients/${id}`, c); }
  archiverClient(id: string) { return this.http.delete<void>(`${this.base}/clients/${id}`); }
  ajouterFichier(clientId: string, type: string, fichier: File, legende?: string) {
    const f = new FormData();
    f.append('type', type);
    f.append('fichier', fichier);
    if (legende) f.append('legende', legende);
    return this.http.post<Asset>(`${this.base}/clients/${clientId}/assets`, f);
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

  portail(jeton: string) { return this.http.get<Portail>(`${this.base}/public/validation/${jeton}`); }
  decider(jeton: string, pubId: string, decision: 'OK' | 'A_MODIFIER', commentaire?: string) {
    return this.http.post<void>(`${this.base}/public/validation/${jeton}/publications/${pubId}`, { decision, commentaire });
  }
  modifierLegendePortail(jeton: string, pubId: string, legende: string, commentaire?: string) {
    return this.http.put<void>(`${this.base}/public/validation/${jeton}/publications/${pubId}/legende`, { legende, commentaire });
  }

  membres() { return this.http.get<Membre[]>(`${this.base}/membres`); }
  inviter(m: { nom: string; email: string; motDePasseProvisoire: string }) { return this.http.post<Membre>(`${this.base}/membres`, m); }
  retirerMembre(id: string) { return this.http.delete<void>(`${this.base}/membres/${id}`); }
  abonnement() { return this.http.get<Abonnement>(`${this.base}/abonnements/courant`); }
  etatIa() { return this.http.get<EtatIa>(`${this.base}/reglages/ia`); }
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
