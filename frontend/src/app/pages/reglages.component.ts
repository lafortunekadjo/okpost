import { Component, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, messageErreur } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { Abonnement, Consommation, EtatIa, Membre } from '../core/models';
import { CatalogueEvenementsComponent } from './catalogue-evenements.component';
import { BibliothequeMusiquesComponent } from '../shared/bibliotheque-musiques.component';

const FORMULES: Partial<Record<string, string>> = { ESSAI: 'Essai gratuit', SOLO: 'Solo', PRO: 'Pro', AGENCE: 'Agence' };

@Component({
  selector: 'app-reglages',
  imports: [FormsModule, DatePipe, DecimalPipe, CatalogueEvenementsComponent, BibliothequeMusiquesComponent],
  template: `
    <div class="page page-etroite">
      <div class="entete-page"><h1>Réglages</h1></div>
      @if (erreur()) { <div class="erreur">{{ erreur() }}</div> }

      @if (abonnement(); as a) {
        <div class="carte">
          <h2>Abonnement</h2>
          <p><strong>{{ formules[a.formule] ?? a.formule }}</strong>
            @if (a.prixMensuelXaf) { · {{ a.prixMensuelXaf | number: '1.0-0' }} FCFA par mois }
            · jusqu'au {{ a.fin | date: 'd MMMM y' }}</p>
          <p class="aide">{{ a.maxClients }} client(s), {{ a.maxMembres }} membre(s), {{ a.calendriersParMois }} calendrier(s) IA et {{ a.imagesParMois }} images IA par mois.</p>
          <p class="aide">Pour changer de formule ou renouveler : paiement Mobile Money, carte ou espèces auprès d'OkPost.</p>
        </div>
      }

      <div class="carte">
        <h2>Membres</h2>
        @for (m of membres(); track m.id) {
          <div class="ligne membre">
            <div class="espace"><strong>{{ m.nom }}</strong> <span class="aide" style="margin-left:8px">{{ m.email }}</span></div>
            <span class="pastille">{{ m.role === 'CM' ? 'Community manager' : 'Admin' }}</span>
            @if (!m.actif) { <span class="pastille">Retiré</span> }
            @else if (auth.estAdmin() && m.id !== auth.session()?.userId) {
              <button class="bouton danger petit" (click)="retirer(m)">Retirer</button>
            }
          </div>
        }
        @if (auth.estAdmin()) {
          <h3 style="margin-top:16px">Ajouter un community manager</h3>
          <form (ngSubmit)="inviter()">
            <div class="ligne-champs">
              <div class="champ"><label>Nom</label><input type="text" name="nom" [(ngModel)]="nouveau.nom" required /></div>
              <div class="champ"><label>E-mail</label><input type="email" name="email" [(ngModel)]="nouveau.email" required /></div>
            </div>
            <div class="champ"><label>Mot de passe provisoire <span class="aide">à lui transmettre ; 8 caractères minimum</span></label>
              <input type="text" name="mdp" [(ngModel)]="nouveau.motDePasseProvisoire" required minlength="8" /></div>
            <button class="bouton">Ajouter</button>
          </form>
        }
      </div>

      @if (ia(); as e) {
        <div class="carte">
          <h2>Intelligence artificielle</h2>
          <p>Fournisseur utilisé : <strong>{{ nomIa(e.fournisseurEspace) }}</strong></p>
          @for (f of fournisseurs(e); track f) {
            <p class="aide">{{ nomIa(f) }} :
              {{ e.configures[f] ? 'clé configurée' : 'clé absente sur le serveur' }}</p>
          }
          <h3 style="margin-top:16px">Images</h3>
          @for (m of moteursImages(e); track m) {
            <p class="aide">{{ m === 'cloudflare' ? 'FLUX via Cloudflare (gratuit)' : m === 'gemini' ? 'Gemini Nano Banana (payant)' : m }} :
              {{ e.images[m] ? 'configuré' : 'non configuré' }}{{ m === e.imageParDefaut ? ' — moteur par défaut' : '' }}</p>
          }
          @if (auth.estSuperAdmin()) {
            <div class="ligne" style="margin-top:8px">
              <label style="margin:0">Fournisseur par défaut de la plateforme</label>
              <select [ngModel]="e.fournisseurParDefaut" (ngModelChange)="changerIa($event)" style="width:auto">
                @for (f of fournisseurs(e); track f) { <option [value]="f">{{ nomIa(f) }}</option> }
              </select>
            </div>
          }
          @if (consommation().length) {
            <h3 style="margin-top:16px">Consommation du mois</h3>
            <table>
              <thead><tr><th>Modèle</th><th>Appels</th><th>Échecs</th><th>Jetons envoyés</th><th>Jetons reçus</th></tr></thead>
              <tbody>
                @for (c of consommation(); track c.modele) {
                  <tr><td>{{ c.modele }}</td><td>{{ c.appels }}</td><td>{{ c.echecs }}</td>
                    <td>{{ c.jetonsEntree | number }}</td><td>{{ c.jetonsSortie | number }}</td></tr>
                }
              </tbody>
            </table>
          }
        </div>
      }
      @if (auth.estSuperAdmin()) { <app-catalogue-evenements /> }
      @if (auth.estSuperAdmin()) { <app-bibliotheque-musiques /> }
    </div>
  `,
  styles: `
    .membre { padding: 10px 0; border-top: 1px solid var(--gris-clair); }
    .membre:first-of-type { border-top: none; }
    table { width: 100%; border-collapse: collapse; font-size: .85rem; }
    th, td { text-align: left; padding: 6px 4px; border-bottom: 1px solid var(--gris-clair); }
    th { color: var(--gris); font-weight: 600; }
  `,
})
export class ReglagesComponent {
  private api = inject(ApiService);
  auth = inject(AuthService);

  membres = signal<Membre[]>([]);
  abonnement = signal<Abonnement | null>(null);
  ia = signal<EtatIa | null>(null);
  consommation = signal<Consommation[]>([]);
  erreur = signal('');
  nouveau = { nom: '', email: '', motDePasseProvisoire: '' };
  formules = FORMULES;

  constructor() {
    this.api.membres().subscribe({ next: m => this.membres.set(m), error: e => this.erreur.set(messageErreur(e)) });
    this.api.abonnement().subscribe({ next: a => this.abonnement.set(a), error: () => {} });
    this.api.etatIa().subscribe({ next: e => this.ia.set(e), error: () => {} });
    if (this.auth.estAdmin()) {
      this.api.consommation().subscribe({ next: c => this.consommation.set(c), error: () => {} });
    }
  }

  nomIa(f: string) {
    return ({ gemini: 'Gemini', claude: 'Claude', compatible: 'Compatible OpenAI (Groq…)' } as Partial<Record<string, string>>)[f] ?? f;
  }

  moteursImages(e: EtatIa) {
    return Object.keys(e.images ?? {});
  }

  fournisseurs(e: EtatIa) {
    return Object.keys(e.configures);
  }

  inviter() {
    this.erreur.set('');
    this.api.inviter(this.nouveau).subscribe({
      next: m => { this.membres.update(l => [...l, m]); this.nouveau = { nom: '', email: '', motDePasseProvisoire: '' }; },
      error: e => this.erreur.set(messageErreur(e)),
    });
  }

  retirer(m: Membre) {
    if (!confirm(`Retirer ${m.nom} ?`)) return;
    this.api.retirerMembre(m.id).subscribe({
      next: () => this.membres.update(l => l.map(x => (x.id === m.id ? { ...x, actif: false } : x))),
      error: e => this.erreur.set(messageErreur(e)),
    });
  }

  changerIa(f: string) {
    this.api.changerIa(f).subscribe({ next: e => this.ia.set(e), error: e => this.erreur.set(messageErreur(e)) });
  }
}
