import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService, messageErreur } from '../core/api.service';
import { BilanPage, Client, Recommandations } from '../core/models';

function iso(d: Date) {
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}

const TYPES: Record<string, string> = { IMAGE: 'Image', VIDEO: 'Vidéo', ALBUM: 'Album photo', LIEN: 'Lien', TEXTE: 'Texte' };

interface Indicateur { nom: string; valeur: number | null | undefined; avant: number | null | undefined; decimales?: boolean; }

/** Bilan Facebook d'une période, comparé à la précédente ; imprimable en PDF aux couleurs du client. */
@Component({
  selector: 'app-bilan',
  imports: [FormsModule, RouterLink, DatePipe, DecimalPipe],
  template: `
    <div class="outils no-print">
      <a [routerLink]="['/clients', id()]" class="bouton secondaire petit">← Retour au client</a>
      <select [(ngModel)]="preset" (ngModelChange)="appliquerPreset()">
        <option value="mois-dernier">Mois dernier</option>
        <option value="ce-mois">Ce mois-ci</option>
        <option value="30j">30 derniers jours</option>
        <option value="perso">Période personnalisée</option>
      </select>
      @if (preset === 'perso') {
        <input type="date" [(ngModel)]="debut" /> <span>au</span> <input type="date" [(ngModel)]="fin" />
        <button class="bouton secondaire petit" (click)="charger()">Afficher</button>
      }
      <span class="espace"></span>
      <button class="bouton" (click)="imprimer()" [disabled]="!bilan()">Télécharger en PDF</button>
    </div>

    <div class="rapport" [style.--marque]="couleur()" [style.--marque-clair]="couleur() + '18'">
      @if (erreur()) { <div class="erreur no-print">{{ erreur() }}</div> }
      @if (chargement()) { <p class="chargement">Lecture des publications de la Page…</p> }
      @if (bilan(); as b) {
        <header class="entete">
          @if (logo()) { <img [src]="logo()" alt="" class="logo" /> }
          <div>
            <p class="surtitre">Bilan Facebook</p>
            <h1>{{ client()?.nom ?? b.page }}</h1>
            <p class="periode">Du {{ b.debut | date: 'd MMMM y' }} au {{ b.fin | date: 'd MMMM y' }}
              <span>· comparé au {{ b.debutPrecedent | date: 'd MMM' }} – {{ b.finPrecedent | date: 'd MMM y' }}</span></p>
            <p class="aide">Page « {{ b.page }} »</p>
          </div>
        </header>

        <section class="kpis">
          @for (k of indicateurs(); track k.nom) {
            <div class="kpi">
              <span class="nom">{{ k.nom }}</span>
              <span class="valeur">{{ k.valeur == null ? '–' : (k.valeur | number: (k.decimales ? '1.0-1' : '1.0-0')) }}</span>
              @if (variation(k); as v) {
                <span class="variation" [class.hausse]="v.signe > 0" [class.baisse]="v.signe < 0">{{ v.texte }}</span>
              }
              <span class="avant">avant : {{ k.avant == null ? '–' : (k.avant | number: (k.decimales ? '1.0-1' : '1.0-0')) }}</span>
            </div>
          }
          <div class="kpi">
            <span class="nom">Abonnés</span>
            <span class="valeur">{{ b.abonnes == null ? '–' : (b.abonnes | number) }}</span>
            @if (b.abonnes != null && b.abonnesDebut != null) {
              <span class="variation" [class.hausse]="b.abonnes > b.abonnesDebut" [class.baisse]="b.abonnes < b.abonnesDebut">
                {{ b.abonnes - b.abonnesDebut >= 0 ? '+' : '' }}{{ b.abonnes - b.abonnesDebut | number }}</span>
              <span class="avant">le {{ b.jourReleveDebut | date: 'd MMM' }} : {{ b.abonnesDebut | number }}</span>
            } @else {
              <span class="avant">évolution disponible après quelques jours de relevés</span>
            }
          </div>
        </section>
        <p class="aide">{{ b.publieesParOkPost }} publication(s) de la période programmée(s) et publiée(s) par OkPost.
          Engagement = réactions + commentaires + partages.</p>
        @if (b.remarque) { <p class="aide no-print avertissement">{{ b.remarque }}</p> }

        <section class="deux">
          <div class="bloc">
            <h2>Par format</h2>
            @for (l of lignes(b.actuelle.engagementParType); track l.cle) {
              <div class="barre-ligne">
                <span class="etiquette">{{ type(l.cle) }} <small>({{ b.actuelle.parType[l.cle] }})</small></span>
                <span class="barre"><span [style.width.%]="l.part"></span></span>
                <span class="chiffre">{{ l.valeur | number: '1.0-1' }}</span>
              </div>
            } @empty { <p class="aide">Aucune publication sur la période.</p> }
            <p class="aide">Engagement moyen par publication.</p>
          </div>
          <div class="bloc">
            <h2>Par jour</h2>
            @for (l of lignes(b.actuelle.engagementParJour, false); track l.cle) {
              <div class="barre-ligne">
                <span class="etiquette">{{ l.cle }}</span>
                <span class="barre"><span [style.width.%]="l.part"></span></span>
                <span class="chiffre">{{ l.valeur | number: '1.0-1' }}</span>
              </div>
            }
            <h2 style="margin-top:14px">Par créneau</h2>
            @for (l of lignes(b.actuelle.engagementParCreneau); track l.cle) {
              <div class="barre-ligne">
                <span class="etiquette">{{ l.cle }}</span>
                <span class="barre"><span [style.width.%]="l.part"></span></span>
                <span class="chiffre">{{ l.valeur | number: '1.0-1' }}</span>
              </div>
            }
          </div>
        </section>

        @if (b.meilleurs.length) {
          <section>
            <h2>Les publications qui ont le mieux marché</h2>
            <div class="posts">
              @for (p of b.meilleurs; track $index) {
                <div class="post">
                  <div class="meta">{{ p.date | date: 'EEE d MMM' }} · {{ type(p.type) }}
                    @if (p.lien) { <a [href]="p.lien" target="_blank" class="no-print">Voir</a> }</div>
                  <p>{{ p.extrait || '(sans texte)' }}</p>
                  <div class="chiffres">👍 {{ p.reactions }}@if (b.commentairesDisponibles) { · 💬 {{ p.commentaires }} } · ↗ {{ p.partages }}@if (p.vues != null) { · 👁 {{ p.vues | number }} }</div>
                </div>
              }
            </div>
          </section>
        }
        @if (b.moinsBons.length) {
          <section>
            <h2>Les moins performantes</h2>
            <div class="posts">
              @for (p of b.moinsBons; track $index) {
                <div class="post faible">
                  <div class="meta">{{ p.date | date: 'EEE d MMM' }} · {{ type(p.type) }}
                    @if (p.lien) { <a [href]="p.lien" target="_blank" class="no-print">Voir</a> }</div>
                  <p>{{ p.extrait || '(sans texte)' }}</p>
                  <div class="chiffres">👍 {{ p.reactions }}@if (b.commentairesDisponibles) { · 💬 {{ p.commentaires }} } · ↗ {{ p.partages }}@if (p.vues != null) { · 👁 {{ p.vues | number }} }</div>
                </div>
              }
            </div>
          </section>
        }

        <section class="analyse">
          <h2>Analyse et recommandations</h2>
          @if (reco(); as r) {
            @if (r.constat) { <p class="constat" contenteditable="true">{{ r.constat }}</p> }
            <ol>
              @for (x of r.recommandations; track $index) { <li contenteditable="true">{{ x }}</li> }
            </ol>
            <p class="aide no-print">Tu peux corriger le texte directement avant de télécharger le PDF.</p>
          }
          <button class="bouton secondaire no-print" (click)="recommander()" [disabled]="recoEnCours()">
            @if (recoEnCours()) { L’IA analyse le bilan… } @else if (reco()) { Rédiger à nouveau } @else { Rédiger l’analyse avec l’IA }
          </button>
          @if (erreurReco()) { <p class="erreur no-print">{{ erreurReco() }}</p> }
        </section>
      }
    </div>
  `,
  styles: `
    :host { display: block; background: #f3f4f6; min-height: 100vh; }
    .outils { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; padding: 12px 16px; background: #fff;
      border-bottom: 1px solid var(--gris-clair); position: sticky; top: 0; z-index: 5; }
    .outils select, .outils input { width: auto; }
    .rapport { max-width: 900px; margin: 20px auto; background: #fff; padding: 32px 36px; border-radius: 12px;
      box-shadow: var(--ombre); --marque: #1b7a3a; }
    .entete { display: flex; gap: 18px; align-items: center; border-bottom: 4px solid var(--marque); padding-bottom: 16px; margin-bottom: 20px; }
    .logo { width: 72px; height: 72px; object-fit: contain; }
    .surtitre { text-transform: uppercase; letter-spacing: .08em; font-size: .75rem; color: var(--marque); font-weight: 700; margin: 0; }
    h1 { margin: 2px 0; font-size: 1.7rem; }
    .periode { margin: 0; font-weight: 600; }
    .periode span { font-weight: 400; color: var(--gris); }
    h2 { font-size: 1.05rem; margin: 22px 0 10px; color: var(--marque); }
    .kpis { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 10px; }
    .kpi { background: var(--marque-clair); border-radius: 10px; padding: 12px; display: flex; flex-direction: column; gap: 2px; }
    .kpi .nom { font-size: .78rem; color: var(--gris); font-weight: 600; }
    .kpi .valeur { font-size: 1.6rem; font-weight: 700; }
    .kpi .variation { font-size: .8rem; font-weight: 700; color: var(--gris); }
    .kpi .variation.hausse { color: #15803d; }
    .kpi .variation.baisse { color: #b91c1c; }
    .kpi .avant { font-size: .74rem; color: var(--gris); }
    .deux { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; }
    .barre-ligne { display: grid; grid-template-columns: 150px 1fr 44px; gap: 8px; align-items: center; margin: 5px 0; font-size: .86rem; }
    .etiquette::first-letter { text-transform: uppercase; }
    .etiquette small { color: var(--gris); }
    .barre { background: #eef0f2; border-radius: 4px; height: 10px; overflow: hidden; }
    .barre span { display: block; height: 100%; background: var(--marque); border-radius: 4px; }
    .chiffre { text-align: right; font-weight: 600; }
    .posts { display: grid; grid-template-columns: repeat(auto-fill, minmax(250px, 1fr)); gap: 10px; }
    .post { border: 1px solid var(--gris-clair); border-left: 4px solid var(--marque); border-radius: 8px; padding: 10px 12px; break-inside: avoid; }
    .post.faible { border-left-color: #d1d5db; }
    .post .meta { font-size: .76rem; color: var(--gris); display: flex; justify-content: space-between; }
    .post .meta::first-letter { text-transform: uppercase; }
    .post p { margin: 6px 0; font-size: .88rem; }
    .post .chiffres { font-size: .8rem; color: var(--gris); }
    .avertissement { background: #fff7e6; border-radius: 8px; padding: 8px 10px; color: #8a5a00; }
    .analyse ol { padding-left: 20px; }
    .analyse li { margin: 6px 0; }
    .constat { background: var(--marque-clair); padding: 10px 12px; border-radius: 8px; }
    [contenteditable]:focus { outline: 2px dashed var(--marque); outline-offset: 2px; }
    @media (max-width: 700px) {
      .rapport { margin: 0; border-radius: 0; padding: 20px 16px; }
      .deux { grid-template-columns: 1fr; }
      .barre-ligne { grid-template-columns: 110px 1fr 40px; }
    }
    @media print {
      :host { background: #fff; }
      .no-print { display: none !important; }
      .rapport { box-shadow: none; margin: 0; padding: 0; max-width: none; }
      section { break-inside: avoid; }
      * { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
    }
  `,
})
export class BilanComponent implements OnInit {
  private api = inject(ApiService);
  id = input.required<string>();
  client = signal<Client | null>(null);
  bilan = signal<BilanPage | null>(null);
  reco = signal<Recommandations | null>(null);
  chargement = signal(false);
  recoEnCours = signal(false);
  erreur = signal('');
  erreurReco = signal('');
  preset = 'mois-dernier';
  debut = '';
  fin = '';

  logo = computed(() => this.client()?.assets?.find(a => a.type === 'LOGO_COULEUR' || a.type === 'ICONE')?.url ?? null);
  couleur = computed(() => {
    const c = this.client()?.couleurs?.find(x => /^#[0-9a-f]{6}$/i.test(x));
    return c ?? '#1b7a3a';
  });

  indicateurs = computed<Indicateur[]>(() => {
    const b = this.bilan();
    if (!b) return [];
    const a = b.actuelle, p = b.precedente;
    const liste: Indicateur[] = [
      { nom: 'Publications', valeur: a.publications, avant: p.publications },
      { nom: 'Réactions', valeur: a.reactions, avant: p.reactions },
      { nom: 'Partages', valeur: a.partages, avant: p.partages },
      { nom: 'Engagement moyen / publication', valeur: a.engagementMoyen, avant: p.engagementMoyen, decimales: true },
    ];
    if (b.commentairesDisponibles) liste.splice(2, 0, { nom: 'Commentaires', valeur: a.commentaires, avant: p.commentaires });
    if (b.vuesDisponibles) liste.splice(liste.length - 1, 0, { nom: 'Vues', valeur: a.vues, avant: p.vues });
    return liste;
  });

  ngOnInit() {
    this.api.client(this.id()).subscribe({ next: c => this.client.set(c), error: () => {} });
    this.appliquerPreset();
  }

  appliquerPreset() {
    const auj = new Date();
    if (this.preset === 'mois-dernier') {
      this.debut = iso(new Date(auj.getFullYear(), auj.getMonth() - 1, 1));
      this.fin = iso(new Date(auj.getFullYear(), auj.getMonth(), 0));
    } else if (this.preset === 'ce-mois') {
      this.debut = iso(new Date(auj.getFullYear(), auj.getMonth(), 1));
      this.fin = iso(auj);
    } else if (this.preset === '30j') {
      this.debut = iso(new Date(auj.getTime() - 29 * 86400000));
      this.fin = iso(auj);
    } else {
      return;
    }
    this.charger();
  }

  charger() {
    if (!this.debut || !this.fin) return;
    this.chargement.set(true);
    this.erreur.set('');
    this.reco.set(null);
    this.api.bilan(this.id(), this.debut, this.fin).subscribe({
      next: b => { this.bilan.set(b); this.chargement.set(false); },
      error: e => { this.erreur.set(messageErreur(e)); this.bilan.set(null); this.chargement.set(false); },
    });
  }

  recommander() {
    this.recoEnCours.set(true);
    this.erreurReco.set('');
    this.api.recommandationsBilan(this.id(), this.debut, this.fin).subscribe({
      next: r => { this.reco.set(r); this.recoEnCours.set(false); },
      error: e => { this.erreurReco.set(messageErreur(e)); this.recoEnCours.set(false); },
    });
  }

  variation(k: Indicateur): { texte: string; signe: number } | null {
    if (k.valeur == null || k.avant == null) return null;
    if (k.avant === 0) return k.valeur === 0 ? { texte: '=', signe: 0 } : { texte: 'nouveau', signe: 1 };
    const pct = Math.round(((k.valeur - k.avant) / k.avant) * 100);
    return { texte: (pct > 0 ? '▲ +' : pct < 0 ? '▼ ' : '') + pct + ' %', signe: Math.sign(pct) };
  }

  /** Lignes de barres : triées par valeur (sauf les jours, dans l'ordre de la semaine). */
  lignes(m: Record<string, number>, trier = true) {
    const entrees = Object.entries(m ?? {});
    const max = Math.max(1, ...entrees.map(([, v]) => v));
    const l = entrees.map(([cle, valeur]) => ({ cle, valeur, part: Math.round((valeur / max) * 100) }));
    return trier ? l.sort((a, b) => b.valeur - a.valeur) : l;
  }

  type(t: string) { return TYPES[t] ?? t; }

  imprimer() {
    const ancien = document.title;
    document.title = `Bilan Facebook ${this.client()?.nom ?? ''} ${this.debut} au ${this.fin}`;
    window.print();
    document.title = ancien;
  }
}

