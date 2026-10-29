import { Component, DestroyRef, computed, inject, input, signal, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService, messageErreur } from '../core/api.service';
import {
  Calendrier, LIBELLES_FORMAT, LIBELLES_RESEAU, LIBELLES_STATUT, LienValidation, Publication, RESEAUX, StatutPublication,
} from '../core/models';

interface Jour { date: string; dansPeriode: boolean; publications: Publication[]; }

@Component({
  selector: 'app-calendrier',
  imports: [RouterLink, DatePipe, FormsModule],
  template: `
    <div class="page">
      @if (erreur()) { <div class="erreur">{{ erreur() }}</div> }
      @if (cal(); as c) {
        <div class="entete-page">
          <div>
            <a [routerLink]="['/clients', c.clientId]" class="aide">← {{ c.clientNom }}</a>
            <h1>{{ c.debut | date: 'd MMMM' }} – {{ c.fin | date: 'd MMMM y' }}</h1>
            <p class="sous-titre">{{ c.total }} publications · {{ nomsReseaux(c.reseaux) }}</p>
          </div>
          <div class="ligne">
            <div class="bascule">
              <button [class.actif]="vue() === 'mois'" (click)="vue.set('mois')">Mois</button>
              <button [class.actif]="vue() === 'liste'" (click)="vue.set('liste')">Liste</button>
            </div>
          </div>
        </div>

        @if (enCours()) {
          <div class="carte progression">
            <strong>{{ c.statutGeneration === 'DETAILS' ? 'Rédaction des publications…' : 'Préparation de la grille…' }}</strong>
            <span class="aide">{{ c.detaillees }} / {{ c.total || '…' }}</span>
            <div class="barre"><div [style.width.%]="c.total ? (c.detaillees / c.total) * 100 : 8"></div></div>
          </div>
        }
        @if (c.statutGeneration === 'ERREUR') {
          <div class="erreur ligne">
            <span class="espace">{{ c.messageErreur || 'La génération a échoué.' }}</span>
            <button class="bouton petit" (click)="relancer()">Relancer</button>
          </div>
        }

        @if (c.strategie) {
          <details class="carte strategie">
            <summary><strong>Stratégie de la période</strong></summary>
            <p>{{ c.strategie }}</p>
            <div class="puces">@for (p of c.piliers; track p) { <span class="puce">{{ p }}</span> }</div>
          </details>
        }

        <div class="outils">
          <select [(ngModel)]="filtreReseau">
            <option value="">Tous les réseaux</option>
            @for (r of reseaux; track r) { <option [value]="r">{{ libellesReseau[r] }}</option> }
          </select>
          <select [(ngModel)]="filtreStatut">
            <option value="">Tous les statuts</option>
            @for (s of statuts; track s) { <option [value]="s">{{ libellesStatut[s] }}</option> }
          </select>
          <span class="espace"></span>
          @if (selection().size) {
            <span class="aide">{{ selection().size }} sélectionnée(s)</span>
            <button class="bouton secondaire petit" (click)="viderSelection()">Annuler</button>
          }
          <button class="bouton" [disabled]="!selection().size || envoiLien()" (click)="creerLien()">Envoyer en validation</button>
        </div>
        <p class="aide">Coche les publications à faire valider. En vue mois, glisse une carte pour changer sa date.</p>

        @if (lienCree(); as l) {
          <div class="carte partage">
            <h2>Lien de validation prêt</h2>
            <p class="aide">Valable jusqu'au {{ l.expireLe | date: 'd MMMM' }}. Le client n'a pas besoin de compte.</p>
            <div class="ligne"><input type="text" [value]="l.url" readonly class="mono" (focus)="$any($event.target).select()" />
              <button class="bouton secondaire" (click)="copier(l.url)">{{ copie() ? 'Copié' : 'Copier' }}</button></div>
            <div class="ligne" style="margin-top:12px">
              <a class="bouton" [href]="lienWhatsApp(l, c)" target="_blank" rel="noopener">Envoyer par WhatsApp</a>
              <input type="email" placeholder="E-mail du client" [(ngModel)]="emailClient" style="max-width:240px" />
              <button class="bouton secondaire" [disabled]="!emailClient" (click)="envoyerEmail(l)">Envoyer par e-mail</button>
            </div>
            @if (messageLien()) { <p class="aide" style="margin-top:8px">{{ messageLien() }}</p> }
          </div>
        }

        @if (vue() === 'mois') {
          <div class="mois">
            @for (j of joursSemaine; track j) { <div class="nom-jour">{{ j }}</div> }
            @for (jour of grille(); track jour.date) {
              <div class="case" [class.hors]="!jour.dansPeriode" (dragover)="$event.preventDefault()" (drop)="deposer($event, jour.date)">
                <div class="num">{{ jour.date | date: 'd' }}</div>
                @for (p of jour.publications; track p.id) {
                  <div class="mini" draggable="true" (dragstart)="glisse = p">
                    <input type="checkbox" [checked]="selection().has(p.id)" (change)="basculer(p.id)" [attr.aria-label]="'Sélectionner ' + p.idee" />
                    <a [routerLink]="['/publications', p.id]">
                      <span class="reseau reseau-{{ p.reseau }}"></span>{{ libellesFormat[p.format] }}
                      <span class="pastille statut-{{ p.statut }}">{{ libellesStatut[p.statut] }}</span>
                      <span class="idee">{{ p.idee }}</span>
                    </a>
                  </div>
                }
              </div>
            }
          </div>
        } @else {
          <div class="liste">
            @for (p of filtrees(); track p.id) {
              <div class="carte publi">
                <input type="checkbox" [checked]="selection().has(p.id)" (change)="basculer(p.id)" />
                <a [routerLink]="['/publications', p.id]" class="corps">
                  <div class="ligne">
                    <strong>{{ p.date | date: 'EEE d MMM' }}</strong>
                    <span><span class="reseau reseau-{{ p.reseau }}"></span>{{ libellesReseau[p.reseau] }} · {{ libellesFormat[p.format] }}</span>
                    @if (p.pilier) { <span class="aide">{{ p.pilier }}</span> }
                    <span class="espace"></span>
                    <span class="pastille statut-{{ p.statut }}">{{ libellesStatut[p.statut] }}</span>
                  </div>
                  <p class="idee-liste">{{ p.idee }}</p>
                  @if (p.legende) { <p class="aide extrait">{{ p.legende }}</p> }
                  @else if (!p.detailGenere) { <p class="aide">Rédaction en cours…</p> }
                </a>
              </div>
            } @empty {
              <p class="vide">Aucune publication pour ces filtres.</p>
            }
          </div>
        }

        @if (liens().length) {
          <div class="carte" style="margin-top:24px">
            <h2>Liens de validation</h2>
            @for (l of liens(); track l.id) {
              <div class="ligne lien">
                <span>{{ l.creeLe | date: 'd MMM, HH:mm' }} · {{ l.publications }} publication(s)
                  @if (l.emailDestinataire) { · {{ l.emailDestinataire }} }</span>
                <span class="espace"></span>
                @if (l.revoque) { <span class="pastille">Révoqué</span> }
                @else {
                  <button class="bouton secondaire petit" (click)="copier(l.url)">Copier</button>
                  <button class="bouton danger petit" (click)="revoquer(l)">Révoquer</button>
                }
              </div>
            }
          </div>
        }

        <div class="ligne" style="margin-top:24px">
          <span class="espace"></span>
          <button class="bouton danger petit" (click)="supprimer(c)">Supprimer ce calendrier</button>
        </div>
      } @else if (!erreur()) {
        <p class="chargement">Chargement…</p>
      }
    </div>
  `,
  styles: `
    .bascule { display: inline-flex; background: #e5e7eb; border-radius: 8px; padding: 3px; }
    .bascule button { font: inherit; font-size: .85rem; font-weight: 600; border: none; background: none; padding: 6px 12px; border-radius: 6px; cursor: pointer; color: var(--gris); }
    .bascule button.actif { background: #fff; color: var(--encre); box-shadow: var(--ombre); }
    .progression { display: grid; grid-template-columns: 1fr auto; gap: 8px; margin-bottom: 16px; }
    .progression .barre { grid-column: 1 / -1; }
    .strategie { margin-bottom: 16px; }
    .strategie summary { cursor: pointer; }
    .strategie p { margin-top: 10px; }
    .outils { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; margin: 8px 0 4px; }
    .outils select { width: auto; }
    .partage { margin: 16px 0; border: 2px solid var(--vert); }
    .mois { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 6px; margin-top: 12px; }
    .nom-jour { font-size: .75rem; font-weight: 700; color: var(--gris); text-transform: uppercase; padding: 0 4px; }
    .case { background: #fff; border-radius: 8px; min-height: 110px; padding: 6px; box-shadow: var(--ombre); }
    .case.hors { background: transparent; box-shadow: none; opacity: .5; }
    .num { font-size: .8rem; font-weight: 700; color: var(--gris); margin-bottom: 4px; }
    .mini { display: flex; gap: 4px; align-items: flex-start; font-size: .75rem; border: 1px solid var(--gris-clair); border-radius: 6px; padding: 4px; margin-bottom: 4px; cursor: grab; background: #fff; }
    .mini input { width: 14px; height: 14px; margin-top: 2px; flex-shrink: 0; }
    .mini a { color: inherit; display: block; min-width: 0; }
    .mini .pastille { font-size: .65rem; padding: 0 6px; }
    .mini .idee { display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; margin-top: 2px; }
    .liste { margin-top: 12px; display: grid; gap: 10px; }
    .publi { display: flex; gap: 12px; align-items: flex-start; padding: 14px 16px; }
    .publi input { margin-top: 4px; }
    .publi .corps { flex: 1; color: inherit; min-width: 0; }
    .publi .corps:hover { text-decoration: none; }
    .idee-liste { margin: 6px 0 4px; font-weight: 500; }
    .extrait { margin: 0; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
    .lien { padding: 8px 0; border-top: 1px solid var(--gris-clair); font-size: .9rem; }
    @media (max-width: 760px) {
      .mois { grid-template-columns: 1fr; }
      .nom-jour, .case.hors { display: none; }
      .case { min-height: 0; }
    }
  `,
})
export class CalendrierComponent implements OnInit {
  private api = inject(ApiService);
  private destroy = inject(DestroyRef);

  id = input.required<string>();
  cal = signal<Calendrier | null>(null);
  liens = signal<LienValidation[]>([]);
  vue = signal<'mois' | 'liste'>(window.innerWidth < 760 ? 'liste' : 'mois');
  selection = signal<Set<string>>(new Set());
  lienCree = signal<LienValidation | null>(null);
  envoiLien = signal(false);
  messageLien = signal('');
  copie = signal(false);
  erreur = signal('');
  filtreReseau = '';
  filtreStatut = '';
  emailClient = '';
  glisse: Publication | null = null;

  reseaux = RESEAUX;
  libellesReseau = LIBELLES_RESEAU;
  libellesFormat = LIBELLES_FORMAT;
  libellesStatut = LIBELLES_STATUT;
  statuts = Object.keys(LIBELLES_STATUT) as StatutPublication[];
  joursSemaine = ['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim'];

  enCours = computed(() => ['EN_ATTENTE', 'GRILLE', 'DETAILS'].includes(this.cal()?.statutGeneration ?? ''));

  private minuterie: ReturnType<typeof setTimeout> | null = null;

  ngOnInit() {
    this.charger();
    this.api.liens(this.id()).subscribe({ next: l => this.liens.set(l), error: () => {} });
    this.destroy.onDestroy(() => this.minuterie && clearTimeout(this.minuterie));
  }

  charger() {
    this.api.calendrier(this.id()).subscribe({
      next: c => {
        this.cal.set(c);
        if (this.enCours()) this.minuterie = setTimeout(() => this.charger(), 3000);
      },
      error: e => this.erreur.set(messageErreur(e)),
    });
  }

  filtrees(): Publication[] {
    return (this.cal()?.publications ?? []).filter(p =>
      (!this.filtreReseau || p.reseau === this.filtreReseau) && (!this.filtreStatut || p.statut === this.filtreStatut));
  }

  grille(): Jour[] {
    const c = this.cal();
    if (!c) return [];
    const debut = new Date(c.debut + 'T00:00:00');
    const fin = new Date(c.fin + 'T00:00:00');
    const lundi = new Date(debut);
    lundi.setDate(lundi.getDate() - ((lundi.getDay() + 6) % 7));
    const dimanche = new Date(fin);
    dimanche.setDate(dimanche.getDate() + (7 - ((dimanche.getDay() + 6) % 7) - 1));
    const parDate = new Map<string, Publication[]>();
    for (const p of this.filtrees()) {
      parDate.set(p.date, [...(parDate.get(p.date) ?? []), p]);
    }
    const jours: Jour[] = [];
    for (const d = new Date(lundi); d <= dimanche; d.setDate(d.getDate() + 1)) {
      const cle = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
      jours.push({ date: cle, dansPeriode: d >= debut && d <= fin, publications: parDate.get(cle) ?? [] });
    }
    return jours;
  }

  viderSelection() {
    this.selection.set(new Set());
  }

  basculer(id: string) {
    const s = new Set(this.selection());
    s.has(id) ? s.delete(id) : s.add(id);
    this.selection.set(s);
  }

  deposer(ev: DragEvent, date: string) {
    ev.preventDefault();
    const p = this.glisse;
    this.glisse = null;
    if (!p || p.date === date) return;
    this.api.modifierPublication(p.id, { date, version: p.version }).subscribe({
      next: maj => this.remplacer(maj),
      error: e => this.erreur.set(messageErreur(e)),
    });
  }

  private remplacer(maj: Publication) {
    const c = this.cal();
    if (!c) return;
    const publications = c.publications.map(p => (p.id === maj.id ? maj : p)).sort((a, b) => a.date.localeCompare(b.date));
    this.cal.set({ ...c, publications });
  }

  creerLien() {
    this.envoiLien.set(true);
    this.erreur.set('');
    this.api.creerLien(this.id(), [...this.selection()]).subscribe({
      next: l => {
        this.lienCree.set(l);
        this.liens.update(x => [l, ...x]);
        this.selection.set(new Set());
        this.envoiLien.set(false);
        this.messageLien.set('');
        this.charger();
      },
      error: e => { this.erreur.set(messageErreur(e)); this.envoiLien.set(false); },
    });
  }

  lienWhatsApp(l: LienValidation, c: Calendrier) {
    const texte = `Bonjour, voici les publications prévues pour ${c.clientNom} à valider : ${l.url}\n` +
      `Pour chacune, vous pouvez valider, modifier le texte ou demander une modification.`;
    return `https://wa.me/?text=${encodeURIComponent(texte)}`;
  }

  envoyerEmail(l: LienValidation) {
    this.api.envoyerLien(l.id, this.emailClient).subscribe({
      next: () => this.messageLien.set(`E-mail envoyé à ${this.emailClient}.`),
      error: e => this.messageLien.set(messageErreur(e)),
    });
  }

  copier(texte: string) {
    navigator.clipboard?.writeText(texte).then(() => {
      this.copie.set(true);
      setTimeout(() => this.copie.set(false), 2000);
    });
  }

  revoquer(l: LienValidation) {
    if (!confirm("Révoquer ce lien ? Le client ne pourra plus l'ouvrir.")) return;
    this.api.revoquerLien(l.id).subscribe({
      next: () => this.liens.update(x => x.map(y => (y.id === l.id ? { ...y, revoque: true } : y))),
      error: e => this.erreur.set(messageErreur(e)),
    });
  }

  relancer() {
    this.api.relancer(this.id()).subscribe({ next: () => this.charger(), error: e => this.erreur.set(messageErreur(e)) });
  }

  supprimer(c: Calendrier) {
    if (!confirm('Supprimer ce calendrier et toutes ses publications ?')) return;
    this.api.supprimerCalendrier(c.id).subscribe({
      next: () => history.back(),
      error: e => this.erreur.set(messageErreur(e)),
    });
  }

  nomsReseaux(r: string[]) {
    return r.map(x => LIBELLES_RESEAU[x] ?? x).join(', ');
  }
}
