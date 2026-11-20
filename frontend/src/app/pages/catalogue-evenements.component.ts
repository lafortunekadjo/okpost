import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, messageErreur } from '../core/api.service';
import { ConfirmationService } from '../core/confirmation.service';
import { EvenementCatalogue } from '../core/models';

const MOIS = ['janv.', 'févr.', 'mars', 'avr.', 'mai', 'juin', 'juil.', 'août', 'sept.', 'oct.', 'nov.', 'déc.'];
const JOURS = ['', 'lundi', 'mardi', 'mercredi', 'jeudi', 'vendredi', 'samedi', 'dimanche'];
const CATEGORIES: Record<string, string> = {
  NATIONAL: 'Fête nationale', RELIGIEUX: 'Religieux', COMMERCIAL: 'Commercial', MONDIAL: 'Journée mondiale', SAISON: 'Saison',
};

/** Super-admin : catalogue des fêtes et journées proposées à la création des calendriers. */
@Component({
  selector: 'app-catalogue-evenements',
  imports: [FormsModule],
  template: `
    <div class="carte">
      <h2>Fêtes et journées</h2>
      <p class="aide">Proposées automatiquement à la création d'un calendrier, selon le secteur et le pays du client.
        Pense à ajouter chaque année les dates des fêtes mobiles (Ramadan, Aïd el-Fitr, Tabaski, Mawlid).</p>
      @if (erreur()) { <div class="erreur">{{ erreur() }}</div> }

      <div class="ligne" style="margin:8px 0">
        <input type="search" [(ngModel)]="filtre" placeholder="Filtrer (nom, secteur, pays)" class="espace" />
        <select [(ngModel)]="categorie" style="width:auto">
          <option value="">Toutes</option>
          @for (c of categories; track c[0]) { <option [value]="c[0]">{{ c[1] }}</option> }
        </select>
      </div>
      <div class="liste">
        @for (e of filtres; track e.id) {
          <div class="ligne ev" [class.inactif]="!e.actif">
            <span class="quand">{{ quand(e) }}</span>
            <span class="espace"><strong>{{ e.nom }}</strong>
              <span class="aide bloc">{{ libelle(e.categorie) }}{{ e.secteurs ? ' · secteurs : ' + e.secteurs : '' }}{{ e.pays ? ' · pays : ' + e.pays : '' }}</span></span>
            <button class="bouton secondaire petit" (click)="basculer(e)">{{ e.actif ? 'Désactiver' : 'Activer' }}</button>
            <button class="bouton danger petit" (click)="supprimer(e)">Supprimer</button>
          </div>
        } @empty { <p class="aide">Aucun événement.</p> }
      </div>

      <h3 style="margin-top:16px">Ajouter</h3>
      <form (ngSubmit)="ajouter()">
        <div class="ligne-champs">
          <div class="champ"><label>Nom</label><input type="text" name="nom" [(ngModel)]="f.nom" required /></div>
          <div class="champ"><label>Catégorie</label>
            <select name="cat" [(ngModel)]="f.categorie">
              @for (c of categories; track c[0]) { <option [value]="c[0]">{{ c[1] }}</option> }
            </select></div>
        </div>
        <div class="ligne-champs">
          <div class="champ"><label>Date</label>
            <select name="type" [(ngModel)]="f.type">
              <option value="FIXE">Même jour chaque année</option><option value="DATE">Date unique (fête mobile, événement daté)</option>
            </select></div>
          @if (f.type === 'FIXE') {
            <div class="champ"><label>Jour</label><input type="number" name="jour" min="1" max="31" [(ngModel)]="f.jour" /></div>
            <div class="champ"><label>Mois</label><select name="mois" [(ngModel)]="f.mois">
              @for (m of mois; track $index) { <option [ngValue]="$index + 1">{{ m }}</option> }</select></div>
          } @else {
            <div class="champ"><label>Le</label><input type="date" name="dateUnique" [(ngModel)]="f.dateUnique" /></div>
          }
          <div class="champ"><label>Durée (jours)</label><input type="number" name="duree" min="1" max="92" [(ngModel)]="f.dureeJours" /></div>
        </div>
        <div class="ligne-champs">
          <div class="champ"><label>Secteurs <span class="aide">mots-clés séparés par des virgules ; vide = tous</span></label>
            <input type="text" name="secteurs" [(ngModel)]="f.secteurs" placeholder="sante, hopital, clinique" /></div>
          <div class="champ"><label>Pays <span class="aide">vide = tous</span></label>
            <input type="text" name="pays" [(ngModel)]="f.pays" placeholder="cameroun" /></div>
        </div>
        <div class="champ"><label>Angle suggéré</label><input type="text" name="angle" [(ngModel)]="f.angle" placeholder="Ce que les marques peuvent publier ce jour-là" /></div>
        <label class="case"><input type="checkbox" name="approx" [(ngModel)]="f.approximative" /> Date approximative (à un jour près)</label>
        <button class="bouton" style="margin-top:10px" [disabled]="!f.nom">Ajouter</button>
      </form>
    </div>
  `,
  styles: `
    .liste { max-height: 420px; overflow: auto; border-top: 1px solid var(--gris-clair); }
    .ev { padding: 8px 0; border-bottom: 1px solid var(--gris-clair); align-items: flex-start; }
    .ev.inactif { opacity: .45; }
    .quand { min-width: 120px; font-size: .85rem; color: var(--gris); }
    .bloc { display: block; }
    .case { display: flex; gap: 8px; align-items: center; font-weight: 500; }
    .case input { width: auto; }
  `,
})
export class CatalogueEvenementsComponent {
  private api = inject(ApiService);
  private confirmation = inject(ConfirmationService);
  private datePipe = new DatePipe('fr-FR');
  evenements = signal<EvenementCatalogue[]>([]);
  erreur = signal('');
  filtre = '';
  categorie = '';
  categories = Object.entries(CATEGORIES);
  mois = MOIS;
  f = this.vide();

  constructor() {
    this.api.catalogueEvenements().subscribe({ next: l => this.evenements.set(l), error: e => this.erreur.set(messageErreur(e)) });
  }

  // Le filtre lit des champs liés par ngModel (pas des signaux) : recalcul à chaque rendu, la liste est courte.
  get filtres() {
    const q = this.filtre.trim().toLowerCase();
    return this.evenements().filter(e => (!this.categorie || e.categorie === this.categorie)
      && (!q || (e.nom + ' ' + e.secteurs + ' ' + e.pays).toLowerCase().includes(q)));
  }

  libelle(c: string) { return CATEGORIES[c] ?? c; }

  quand(e: EvenementCatalogue) {
    const duree = e.dureeJours > 1 ? ` (${e.dureeJours} j)` : '';
    switch (e.regle) {
      case 'FIXE': return `${e.jour} ${MOIS[(e.mois ?? 1) - 1]}${duree}`;
      case 'DATE': return (this.datePipe.transform(e.dateUnique, 'd MMM y') ?? '') + duree;
      case 'PAQUES': return e.decalage === 0 ? 'Pâques' : `Pâques ${e.decalage > 0 ? '+' : ''}${e.decalage} j`;
      case 'NIEME': {
        const rang = (e.nieme ?? 0) < 0 ? 'dernier' : e.nieme === 1 ? '1er' : `${e.nieme}e`;
        return `${rang} ${JOURS[e.jourSemaine ?? 0]} de ${MOIS[(e.mois ?? 1) - 1]}${e.decalage ? ` +${e.decalage} j` : ''}`;
      }
      default: return '';
    }
  }

  basculer(e: EvenementCatalogue) {
    this.api.activerEvenement(e.id, !e.actif).subscribe({
      next: m => this.evenements.update(l => l.map(x => (x.id === m.id ? m : x))),
      error: err => this.erreur.set(messageErreur(err)),
    });
  }

  async supprimer(e: EvenementCatalogue) {
    if (!(await this.confirmation.demander({ titre: `Supprimer « ${e.nom} » du catalogue ?`, confirmer: 'Supprimer', danger: true }))) return;
    this.api.supprimerEvenement(e.id).subscribe({
      next: () => this.evenements.update(l => l.filter(x => x.id !== e.id)),
      error: err => this.erreur.set(messageErreur(err)),
    });
  }

  ajouter() {
    this.erreur.set('');
    const f = this.f;
    this.api.ajouterEvenement({
      nom: f.nom, categorie: f.categorie, dureeJours: f.dureeJours, secteurs: f.secteurs, pays: f.pays, angle: f.angle,
      approximative: f.approximative,
      ...(f.type === 'FIXE' ? { mois: f.mois, jour: f.jour } : { dateUnique: f.dateUnique }),
    }).subscribe({
      next: e => { this.evenements.update(l => [...l, e]); this.f = this.vide(); },
      error: err => this.erreur.set(messageErreur(err)),
    });
  }

  private vide() {
    return { nom: '', categorie: 'MONDIAL', type: 'FIXE', jour: 1, mois: 1, dateUnique: '', dureeJours: 1,
      secteurs: '', pays: '', angle: '', approximative: false };
  }
}
