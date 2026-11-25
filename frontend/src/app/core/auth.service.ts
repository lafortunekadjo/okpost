import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, map, of, shareReplay, switchMap, tap } from 'rxjs';
import { SessionUtilisateur } from './models';

/** Le jeton d'accès reste en mémoire ; le jeton de rafraîchissement est dans un cookie HttpOnly. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);

  readonly session = signal<SessionUtilisateur | null>(null);
  readonly connecte = computed(() => this.session() !== null);
  readonly estAdmin = computed(() => ['ADMIN', 'SUPER_ADMIN'].includes(this.session()?.role ?? ''));
  readonly estSuperAdmin = computed(() => this.session()?.role === 'SUPER_ADMIN');
  /** Invité sur un client d'un autre espace : la session ne donne accès qu'à ce client. */
  readonly estCollaborateur = computed(() => this.session()?.role === 'COLLAB');
  /** Nom du client partagé sur lequel on travaille (pour le bandeau). */
  readonly contexteNom = signal('');

  private static readonly CLE_CONTEXTE = 'okpost-contexte';

  private rafraichissementEnCours: Observable<boolean> | null = null;

  connexion(email: string, motDePasse: string) {
    return this.http.post<SessionUtilisateur>('/api/v1/auth/connexion', { email, motDePasse })
      .pipe(tap(s => this.session.set(s)));
  }

  inscription(donnees: { nomEspace: string; nom: string; email: string; motDePasse: string }) {
    return this.http.post<SessionUtilisateur>('/api/v1/auth/inscription', donnees)
      .pipe(tap(s => this.session.set(s)));
  }

  /**
   * Ouvre une session limitée à un client partagé : le jeton d'accès est remplacé, le cookie de rafraîchissement reste celui de l'utilisateur.
   * Le client est mémorisé pour la durée de l'onglet : un rechargement ou une expiration du jeton y ramène.
   */
  entrerContexte(clientId: string, nom: string): Observable<void> {
    return this.http.post<SessionUtilisateur>('/api/v1/acces/contexte', { clientId }).pipe(
      tap(s => {
        this.session.set(s);
        this.contexteNom.set(nom);
        try { sessionStorage.setItem(AuthService.CLE_CONTEXTE, JSON.stringify({ clientId, nom })); } catch { /* stockage indisponible */ }
      }),
      map(() => undefined),
    );
  }

  /** Revient à son propre espace. */
  quitterContexte(): Observable<boolean> {
    this.oublierContexte();
    return this.rafraichir();
  }

  private oublierContexte() {
    this.contexteNom.set('');
    try { sessionStorage.removeItem(AuthService.CLE_CONTEXTE); } catch { /* ignoré */ }
  }

  /** Après un rafraîchissement, on retourne dans le client partagé où l'on travaillait (si l'accès existe toujours). */
  private rejouerContexte(): Observable<unknown> {
    let memo: { clientId: string; nom: string } | null = null;
    try { memo = JSON.parse(sessionStorage.getItem(AuthService.CLE_CONTEXTE) ?? 'null'); } catch { memo = null; }
    if (!memo?.clientId) return of(null);
    return this.entrerContexte(memo.clientId, memo.nom ?? '').pipe(catchError(() => { this.oublierContexte(); return of(null); }));
  }

  /** Tente de rétablir la session à partir du cookie (au démarrage ou après un 401). */
  rafraichir(): Observable<boolean> {
    if (!this.rafraichissementEnCours) {
      this.rafraichissementEnCours = this.http.post<SessionUtilisateur>('/api/v1/auth/rafraichir', {}).pipe(
        tap(s => this.session.set(s)),
        switchMap(() => this.rejouerContexte()),
        map(() => true),
        catchError(() => { this.session.set(null); return of(false); }),
        finalize(() => (this.rafraichissementEnCours = null)),
        shareReplay(1),
      );
    }
    return this.rafraichissementEnCours;
  }

  deconnexion() {
    this.http.post('/api/v1/auth/deconnexion', {}).subscribe({ complete: () => {}, error: () => {} });
    this.oublierContexte();
    this.session.set(null);
    this.router.navigateByUrl('/connexion');
  }
}
