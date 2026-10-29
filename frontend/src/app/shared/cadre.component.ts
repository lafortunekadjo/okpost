import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { LogoComponent } from './logo.component';

@Component({
  selector: 'app-cadre',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, LogoComponent],
  template: `
    <header class="barre-haut">
      <div class="contenu">
        <a routerLink="/" class="marque"><app-logo /></a>
        <nav>
          <a routerLink="/" routerLinkActive="actif" [routerLinkActiveOptions]="{ exact: true }">Clients</a>
          <a routerLink="/reglages" routerLinkActive="actif">Réglages</a>
        </nav>
        <span class="espace"></span>
        <span class="nom">{{ auth.session()?.nom }}</span>
        <button class="bouton secondaire petit" (click)="auth.deconnexion()">Déconnexion</button>
      </div>
    </header>
    <main><router-outlet /></main>
  `,
  styles: `
    .barre-haut { background: #fff; border-bottom: 1px solid var(--gris-clair); position: sticky; top: 0; z-index: 10; }
    .contenu { max-width: 1120px; margin: 0 auto; padding: 10px 16px; display: flex; align-items: center; gap: 16px; }
    .marque:hover { text-decoration: none; }
    nav { display: flex; gap: 4px; }
    nav a { color: var(--gris); font-weight: 600; font-size: .9rem; padding: 6px 10px; border-radius: 8px; }
    nav a:hover { background: #f3f4f6; text-decoration: none; }
    nav a.actif { color: var(--vert-fonce); background: var(--vert-clair); }
    .nom { color: var(--gris); font-size: .85rem; }
    @media (max-width: 640px) { .nom { display: none; } .contenu { gap: 8px; } }
  `,
})
export class CadreComponent {
  auth = inject(AuthService);
}
