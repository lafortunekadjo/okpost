import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, map, of, shareReplay, tap } from 'rxjs';
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

  private rafraichissementEnCours: Observable<boolean> | null = null;

  connexion(email: string, motDePasse: string) {
    return this.http.post<SessionUtilisateur>('/api/v1/auth/connexion', { email, motDePasse })
      .pipe(tap(s => this.session.set(s)));
  }

  inscription(donnees: { nomEspace: string; nom: string; email: string; motDePasse: string }) {
    return this.http.post<SessionUtilisateur>('/api/v1/auth/inscription', donnees)
      .pipe(tap(s => this.session.set(s)));
  }

  /** Tente de rétablir la session à partir du cookie (au démarrage ou après un 401). */
  rafraichir(): Observable<boolean> {
    if (!this.rafraichissementEnCours) {
      this.rafraichissementEnCours = this.http.post<SessionUtilisateur>('/api/v1/auth/rafraichir', {}).pipe(
        tap(s => this.session.set(s)),
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
    this.session.set(null);
    this.router.navigateByUrl('/connexion');
  }
}
