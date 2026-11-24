import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, messageErreur } from '../core/api.service';
import { AdminCompte, AdminJournal, AdminTotaux } from '../core/models';

type Onglet = 'comptes' | 'journal';

/** Espace du propriétaire de la plateforme : comptes, usage, consommation d'IA et journal « qui a fait quoi ». */
@Component({
  selector: 'app-admin',
  imports: [DatePipe, FormsModule],
  template: `
    <div class="page">
      <h1>Administration</h1>
      <p class="aide">Vue d'ensemble de tous les comptes de la plateforme. Visible uniquement par le super-admin.</p>
      @if (erreur()) { <div class="erreur" role="alert">{{ erreur() }}</div> }

      @if (totaux(); as t) {
        <div class="chiffres">
          <div class="carte"><strong>{{ t.comptes }}</strong><span>comptes</span></div>
          <div class="carte"><strong>{{ t.clients }}</strong><span>clients actifs</span></div>
          <div class="carte"><strong>{{ t.calendriers }}</strong><span>calendriers</span></div>
          <div class="carte"><strong>{{ t.publieesMois }}</strong><span>publiées ce mois</span></div>
          <div class="carte"><strong>{{ t.appelsIaMois }}</strong><span>appels IA ce mois @if (t.echecsIaMois) { <em>({{ t.echecsIaMois }} en échec)</em> }</span></div>
          <div class="carte"><strong>{{ t.imagesMois }}</strong><span>images IA ce mois</span></div>
          <div class="carte" [class.alerte]="t.echecsPublication7j > 0"><strong>{{ t.echecsPublication7j }}</strong><span>échecs de publication (7 j)</span></div>
        </div>
      }

      <div class="onglets" role="tablist">
        <button type="button" role="tab" [class.actif]="onglet() === 'comptes'" [attr.aria-selected]="onglet() === 'comptes'" (click)="onglet.set('comptes')">Comptes</button>
        <button type="button" role="tab" [class.actif]="onglet() === 'journal'" [attr.aria-selected]="onglet() === 'journal'" (click)="ouvrirJournal(null)">Journal d'activité</button>
      </div>

      @if (onglet() === 'comptes') {
        <div class="carte defile">
          <table>
            <thead>
              <tr>
                <th>Compte</th><th>Créé le</th><th>Dernière connexion</th><th>Abonnement</th>
                <th class="num">Membres</th><th class="num">Clients</th><th class="num">Calendriers</th><th class="num">Publiées / total</th>
                <th class="num">Appels IA (mois)</th><th class="num">Images IA (mois)</th><th></th>
              </tr>
            </thead>
            <tbody>
              @for (c of comptes(); track c.id) {
                <tr>
                  <td><strong>{{ c.nom }}</strong><br /><span class="aide">{{ c.emailAdmin }}</span></td>
                  <td>{{ c.creeLe | date: 'd MMM y' }}</td>
                  <td>@if (c.derniereConnexion) { {{ c.derniereConnexion | date: 'd MMM y, HH:mm' }} } @else { <span class="aide">jamais</span> }</td>
                  <td>{{ c.formule }}<br /><span class="aide">{{ c.statutAbonnement }} @if (c.finAbonnement) { · jusqu'au {{ c.finAbonnement | date: 'd MMM y' }} }</span></td>
                  <td class="num">{{ c.membres }}</td>
                  <td class="num">{{ c.clients }}</td>
                  <td class="num">{{ c.calendriers }}</td>
                  <td class="num">{{ c.publiees }} / {{ c.publications }}</td>
                  <td class="num">{{ c.appelsIaMois }}</td>
                  <td class="num">{{ c.imagesMois }}</td>
                  <td><button type="button" class="bouton secondaire petit" (click)="ouvrirJournal(c.id)">Activité</button></td>
                </tr>
              } @empty {
                <tr><td colspan="11" class="aide">{{ chargement() ? 'Chargement…' : 'Aucun compte.' }}</td></tr>
              }
            </tbody>
          </table>
        </div>
      }

      @if (onglet() === 'journal') {
        <div class="carte">
          <div class="ligne filtres">
            <select [(ngModel)]="filtreEspace" (ngModelChange)="filtrer()" aria-label="Filtrer par compte">
              <option value="">Tous les comptes</option>
              @for (c of comptes(); track c.id) { <option [value]="c.id">{{ c.nom }}</option> }
            </select>
            <select [(ngModel)]="filtreAction" (ngModelChange)="filtrer()" aria-label="Filtrer par action">
              <option value="">Toutes les actions</option>
              @for (a of listeActions(); track a[0]) { <option [value]="a[0]">{{ a[1] }}</option> }
            </select>
            <span class="espace"></span>
            <span class="aide">{{ journal()?.total ?? 0 }} ligne(s) · conservées 12 mois</span>
          </div>
          <div class="defile">
            <table>
              <thead><tr><th>Quand</th><th>Qui</th><th>Action</th><th>Sur quoi</th><th>Détail</th><th>Compte</th></tr></thead>
              <tbody>
                @for (l of journal()?.lignes ?? []; track $index) {
                  <tr [class.rouge]="l.action === 'PUBLICATION_ECHEC' || l.action === 'CONNEXION_ECHEC'">
                    <td class="nowrap">{{ l.date | date: 'd MMM, HH:mm:ss' }}</td>
                    <td>{{ l.auteur }}</td>
                    <td>{{ l.libelle }}</td>
                    <td>{{ l.cible }}</td>
                    <td class="detail">{{ l.detail }}</td>
                    <td>{{ l.espace }}</td>
                  </tr>
                } @empty {
                  <tr><td colspan="6" class="aide">{{ chargement() ? 'Chargement…' : 'Aucune activité pour ce filtre.' }}</td></tr>
                }
              </tbody>
            </table>
          </div>
          @if (journal(); as j) {
            <div class="ligne pagination">
              <button type="button" class="bouton secondaire petit" [disabled]="j.page === 0" (click)="aller(j.page - 1)">Précédent</button>
              <span class="aide">Page {{ j.page + 1 }} sur {{ pages(j.total, j.taille) }}</span>
              <button type="button" class="bouton secondaire petit" [disabled]="(j.page + 1) * j.taille >= j.total" (click)="aller(j.page + 1)">Suivant</button>
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: `
    .chiffres { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 10px; margin: 14px 0; }
    .chiffres .carte { display: flex; flex-direction: column; gap: 2px; padding: 12px 14px; }
    .chiffres strong { font-size: 1.6rem; }
    .chiffres span { color: var(--gris); font-size: .8rem; }
    .chiffres .alerte { border-left: 4px solid var(--rouge, #dc2626); }
    .onglets { display: flex; gap: 4px; margin: 16px 0 10px; }
    .onglets button { border: 0; background: transparent; padding: 8px 14px; border-radius: 8px; font-weight: 600; color: var(--gris); cursor: pointer; }
    .onglets button.actif { background: var(--vert-clair); color: var(--vert-fonce); }
    .defile { overflow-x: auto; }
    table { width: 100%; border-collapse: collapse; font-size: .85rem; }
    th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid var(--gris-clair); vertical-align: top; }
    th { color: var(--gris); font-weight: 600; white-space: nowrap; }
    .num { text-align: right; font-variant-numeric: tabular-nums; }
    .nowrap { white-space: nowrap; }
    .detail { max-width: 320px; overflow-wrap: anywhere; }
    tr.rouge td { background: #fef2f2; }
    .filtres { gap: 8px; margin-bottom: 10px; flex-wrap: wrap; }
    .pagination { justify-content: center; gap: 12px; margin-top: 12px; }
  `,
})
export class AdminComponent implements OnInit {
  private api = inject(ApiService);

  onglet = signal<Onglet>('comptes');
  comptes = signal<AdminCompte[]>([]);
  totaux = signal<AdminTotaux | null>(null);
  journal = signal<AdminJournal | null>(null);
  erreur = signal('');
  chargement = signal(true);
  filtreEspace = '';
  filtreAction = '';

  ngOnInit() {
    this.api.adminComptes().subscribe({
      next: v => { this.comptes.set(v.comptes); this.totaux.set(v.totaux); this.chargement.set(false); },
      error: e => { this.erreur.set(messageErreur(e)); this.chargement.set(false); },
    });
  }

  listeActions(): [string, string][] {
    return Object.entries(this.journal()?.actions ?? {});
  }

  ouvrirJournal(espaceId: string | null) {
    this.onglet.set('journal');
    this.filtreEspace = espaceId ?? '';
    this.filtreAction = '';
    this.aller(0);
  }

  filtrer() {
    this.aller(0);
  }

  aller(page: number) {
    this.chargement.set(true);
    this.api.adminJournal({ espace: this.filtreEspace || undefined, action: this.filtreAction || undefined, page }).subscribe({
      next: j => { this.journal.set(j); this.chargement.set(false); },
      error: e => { this.erreur.set(messageErreur(e)); this.chargement.set(false); },
    });
  }

  pages(total: number, taille: number) {
    return Math.max(1, Math.ceil(total / taille));
  }
}
