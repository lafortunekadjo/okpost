import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { messageErreur } from '../core/api.service';
import { LogoComponent } from '../shared/logo.component';

@Component({
  selector: 'app-inscription',
  imports: [FormsModule, RouterLink, LogoComponent],
  template: `
    <div class="ecran-acces">
      <div class="carte boite">
        <div class="tete"><app-logo [taille]="24" /></div>
        <h1>Créer ton espace</h1>
        <p class="sous-titre">14 jours gratuits, toutes les fonctions, sans carte.</p>
        @if (erreur()) { <div class="erreur">{{ erreur() }}</div> }
        <form (ngSubmit)="valider()">
          <div class="champ"><label for="espace">Nom de ton agence ou de ton activité</label>
            <input id="espace" type="text" name="espace" [(ngModel)]="d.nomEspace" required /></div>
          <div class="champ"><label for="nom">Ton nom</label>
            <input id="nom" type="text" name="nom" [(ngModel)]="d.nom" required autocomplete="name" /></div>
          <div class="champ"><label for="email">E-mail</label>
            <input id="email" type="email" name="email" [(ngModel)]="d.email" required autocomplete="email" /></div>
          <div class="champ"><label for="mdp">Mot de passe <span class="aide">8 caractères minimum</span></label>
            <input id="mdp" type="password" name="mdp" [(ngModel)]="d.motDePasse" required minlength="8" autocomplete="new-password" /></div>
          <button class="bouton plein" [disabled]="envoi()">{{ envoi() ? 'Création…' : 'Commencer' }}</button>
        </form>
        <p class="bas">Déjà un compte ? <a routerLink="/connexion">Se connecter</a></p>
      </div>
    </div>
  `,
  styleUrl: './acces.css',
})
export class InscriptionComponent {
  private auth = inject(AuthService);
  private router = inject(Router);
  d = { nomEspace: '', nom: '', email: '', motDePasse: '' };
  envoi = signal(false);
  erreur = signal('');

  valider() {
    this.envoi.set(true);
    this.erreur.set('');
    this.auth.inscription(this.d).subscribe({
      next: () => this.router.navigateByUrl('/clients/nouveau'),
      error: e => { this.erreur.set(messageErreur(e)); this.envoi.set(false); },
    });
  }
}
