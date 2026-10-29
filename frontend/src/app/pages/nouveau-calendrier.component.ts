import { Component, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService, messageErreur } from '../core/api.service';
import { LIBELLES_RESEAU, RESEAUX } from '../core/models';

function iso(d: Date) {
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}

@Component({
  selector: 'app-nouveau-calendrier',
  imports: [FormsModule, RouterLink],
  template: `
    <div class="page page-etroite">
      <div class="entete-page">
        <div>
          <h1>Nouveau calendrier</h1>
          <p class="sous-titre">L'IA prépare la grille, puis rédige chaque publication.</p>
        </div>
        <a class="bouton secondaire" [routerLink]="['/clients', clientId()]">Annuler</a>
      </div>
      @if (erreur()) { <div class="erreur">{{ erreur() }}</div> }
      <form class="carte" (ngSubmit)="generer()">
        <div class="ligne-champs">
          <div class="champ"><label>Du</label><input type="date" name="debut" [(ngModel)]="debut" required /></div>
          <div class="champ"><label>Au <span class="aide">6 semaines maximum</span></label><input type="date" name="fin" [(ngModel)]="fin" required /></div>
        </div>
        <label>Réseaux et publications par semaine</label>
        <div class="reseaux">
          @for (r of reseaux; track r) {
            <div class="reseau-ligne">
              <label class="case"><input type="checkbox" [name]="'actif-' + r" [(ngModel)]="actifs[r]" />
                <span class="reseau reseau-{{ r }}"></span>{{ libelles[r] }}</label>
              <input type="number" min="1" max="14" [name]="'rythme-' + r" [(ngModel)]="rythme[r]" [disabled]="!actifs[r]" />
              <span class="aide">par semaine</span>
            </div>
          }
        </div>
        <div class="champ"><label>Objectifs de la période</label>
          <textarea name="objectifs" rows="3" [(ngModel)]="objectifs" placeholder="Ex. faire connaître la nouvelle formation, remplir la session de décembre"></textarea></div>
        <div class="champ"><label>Temps forts à couvrir <span class="aide">promotions, événements, fêtes</span></label>
          <textarea name="temps" rows="2" [(ngModel)]="tempsForts" placeholder="Ex. salon de l'agriculture le 12, Noël"></textarea></div>
        <div class="champ"><label>Langue</label>
          <select name="langue" [(ngModel)]="langue">
            <option value="">Celle de la fiche client</option><option value="fr">Français</option><option value="en">Anglais</option>
          </select></div>
        <button class="bouton plein" [disabled]="envoi()">{{ envoi() ? 'Lancement…' : 'Générer le calendrier' }}</button>
        <p class="aide" style="margin-top:10px;text-align:center">Compte 1 à 3 minutes. Tu peux quitter la page pendant la génération.</p>
      </form>
    </div>
  `,
  styles: `
    .reseaux { margin-bottom: 16px; }
    .reseau-ligne { display: flex; align-items: center; gap: 12px; padding: 8px 0; border-bottom: 1px solid var(--gris-clair); }
    .reseau-ligne input[type=number] { width: 72px; }
    .case { display: flex; align-items: center; gap: 8px; margin: 0; flex: 1; font-weight: 500; cursor: pointer; }
  `,
})
export class NouveauCalendrierComponent {
  private api = inject(ApiService);
  private router = inject(Router);
  clientId = input.required<string>({ alias: 'id' });

  reseaux = RESEAUX;
  libelles = LIBELLES_RESEAU;
  debut = iso(new Date(new Date().getFullYear(), new Date().getMonth() + 1, 1));
  fin = iso(new Date(new Date().getFullYear(), new Date().getMonth() + 2, 0));
  actifs: Record<string, boolean> = { FACEBOOK: true, INSTAGRAM: true, TIKTOK: false, LINKEDIN: false };
  rythme: Record<string, number> = { FACEBOOK: 3, INSTAGRAM: 3, TIKTOK: 2, LINKEDIN: 2 };
  objectifs = '';
  tempsForts = '';
  langue = '';
  envoi = signal(false);
  erreur = signal('');

  generer() {
    const r: Record<string, number> = {};
    for (const x of RESEAUX) if (this.actifs[x]) r[x] = this.rythme[x];
    if (!Object.keys(r).length) { this.erreur.set('Choisis au moins un réseau.'); return; }
    this.envoi.set(true);
    this.erreur.set('');
    this.api.creerCalendrier(this.clientId(), {
      debut: this.debut, fin: this.fin, rythme: r, objectifs: this.objectifs, tempsForts: this.tempsForts,
      langue: this.langue || null,
    }).subscribe({
      next: c => this.router.navigate(['/calendriers', c.id]),
      error: e => { this.erreur.set(messageErreur(e)); this.envoi.set(false); },
    });
  }
}
