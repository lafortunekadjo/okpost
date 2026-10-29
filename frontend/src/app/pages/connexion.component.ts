import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { messageErreur } from '../core/api.service';
import { LogoComponent } from '../shared/logo.component';

@Component({
  selector: 'app-connexion',
  imports: [FormsModule, RouterLink, LogoComponent],
  template: `
    <div class="ecran-acces">
      <div class="carte boite">
        <div class="tete"><app-logo [taille]="24" /></div>
        <h1>Connexion</h1>
        <p class="sous-titre">Ton mois de contenu, validé.</p>
        @if (erreur()) { <div class="erreur">{{ erreur() }}</div> }
        <form (ngSubmit)="valider()">
          <div class="champ"><label for="email">E-mail</label>
            <input id="email" type="email" name="email" [(ngModel)]="email" required autocomplete="email" /></div>
          <div class="champ"><label for="mdp">Mot de passe</label>
            <input id="mdp" type="password" name="mdp" [(ngModel)]="motDePasse" required autocomplete="current-password" /></div>
          <button class="bouton plein" [disabled]="envoi()">{{ envoi() ? 'Connexion…' : 'Se connecter' }}</button>
        </form>
        <p class="bas">Pas encore de compte ? <a routerLink="/inscription">Essai gratuit de 14 jours</a></p>
      </div>
    </div>
  `,
  styleUrl: './acces.css',
})
export class ConnexionComponent {
  private auth = inject(AuthService);
  private router = inject(Router);
  email = '';
  motDePasse = '';
  envoi = signal(false);
  erreur = signal('');

  valider() {
    this.envoi.set(true);
    this.erreur.set('');
    this.auth.connexion(this.email, this.motDePasse).subscribe({
      next: () => this.router.navigateByUrl('/'),
      error: e => { this.erreur.set(messageErreur(e)); this.envoi.set(false); },
    });
  }
}
