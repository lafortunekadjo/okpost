import { Component, OnInit, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService, messageErreur } from '../core/api.service';
import {
  Calendrier, EvaluationCalendrier, LIBELLES_RESEAU, LigneSynthese, MoyennesEvaluation, NotesPublication, Publication,
} from '../core/models';

type Critere = 'specificite' | 'exactitude' | 'contexte' | 'faisabilite' | 'pret';

const CRITERES: { cle: Critere; nom: string; aide: string }[] = [
  { cle: 'specificite', nom: 'Spécificité', aide: '5 = ne pourrait servir qu’à ce client ; 1 = générique, valable pour n’importe qui' },
  { cle: 'exactitude', nom: 'Exactitude', aide: '5 = aucun prix, chiffre, témoignage ou numéro inventé ; 1 = informations fausses' },
  { cle: 'contexte', nom: 'Contexte local', aide: '5 = dates justes, références locales crédibles ; 1 = hors sujet ou faux' },
  { cle: 'faisabilite', nom: 'Faisabilité', aide: '5 = le client peut produire ce visuel ou cette vidéo facilement ; 1 = irréaliste' },
  { cle: 'pret', nom: 'Prêt à publier', aide: '5 = envoyable tel quel ; 4 = petites retouches ; 3 = retouches moyennes ; 1-2 = à réécrire' },
];

@Component({
  selector: 'app-evaluation',
  imports: [FormsModule, RouterLink, DatePipe],
  template: `
    <div class="page">
      @if (erreur()) { <div class="erreur">{{ erreur() }}</div> }
      @if (calendrier(); as c) {
        <div class="entete-page">
          <div>
            <a [routerLink]="['/calendriers', c.id]" class="aide">← Retour au calendrier</a>
            <h1>Noter la qualité · {{ c.clientNom }}</h1>
            <p class="sous-titre">{{ c.debut | date: 'd MMM' }} – {{ c.fin | date: 'd MMM y' }} · généré avec
              <strong>{{ evaluation()?.modele ?? c.fournisseurIa ?? 'inconnu' }}</strong></p>
          </div>
        </div>

        <div class="carte resume">
          @if (evaluation(); as e) {
            <div class="chiffres">
              <div><span class="grand">{{ e.moyennes.globale ?? '–' }}</span><span class="aide">note moyenne / 5</span></div>
              <div><span class="grand">{{ e.moyennes.partEnvoyables != null ? e.moyennes.partEnvoyables + ' %' : '–' }}</span>
                <span class="aide">envoyables avec peu ou pas de retouches (objectif 70 %)</span></div>
              <div><span class="grand">{{ e.moyennes.evaluees }}/{{ e.total }}</span><span class="aide">publications notées</span></div>
            </div>
            <div class="criteres-moy">
              @for (k of criteres; track k.cle) {
                <span>{{ k.nom }} <strong>{{ moyenne(e.moyennes, k.cle) ?? '–' }}</strong></span>
              }
            </div>
          }
          <hr />
          <div class="ligne-champs">
            <div class="champ"><label>Équilibre du calendrier <span class="aide">mélange des formats, des objectifs et des piliers ; pas de doublons</span></label>
              <div class="echelle">
                @for (v of [1,2,3,4,5]; track v) {
                  <button type="button" [class.choisi]="equilibre === v" (click)="equilibre = equilibre === v ? null : v; enregistrerAvis()">{{ v }}</button>
                }
              </div></div>
          </div>
          <div class="champ"><label>Avis global <span class="aide">ce qui manque, ce qui revient trop, ce qui t’a surprise</span></label>
            <textarea rows="3" [(ngModel)]="avis" (blur)="enregistrerAvis()"></textarea></div>
        </div>

        <details class="carte legende-criteres">
          <summary>Comment noter (1 à 5)</summary>
          @for (k of criteres; track k.cle) { <p><strong>{{ k.nom }}</strong> : {{ k.aide }}</p> }
        </details>

        @for (p of c.publications; track p.id) {
          <div class="carte pub" [class.notee]="notee(p.id)">
            <div class="tete">
              <span class="aide">{{ p.date | date: 'EEE d MMM' }} · <span class="reseau reseau-{{ p.reseau }}"></span>{{ libelles[p.reseau] }} · {{ p.format }}{{ p.pilier ? ' · ' + p.pilier : '' }}</span>
              <a [routerLink]="['/publications', p.id]" class="aide" target="_blank">Ouvrir</a>
            </div>
            <p class="idee"><strong>{{ p.idee }}</strong>@if (p.tempsFort) { <span class="pastille">{{ p.tempsFort }}</span> }</p>
            @if (p.legende) {
              <p class="legende" [class.ouverte]="ouvertes().has(p.id)" (click)="basculer(p.id)">{{ p.legende }}</p>
            }
            @if (p.cta) { <p class="aide">CTA : {{ p.cta }}</p> }
            <div class="grille">
              @for (k of criteres; track k.cle) {
                <div class="critere" [title]="k.aide">
                  <span class="nom">{{ k.nom }}</span>
                  <div class="echelle">
                    @for (v of [1,2,3,4,5]; track v) {
                      <button type="button" [class.choisi]="note(p.id, k.cle) === v" (click)="choisir(p.id, k.cle, v)">{{ v }}</button>
                    }
                  </div>
                </div>
              }
            </div>
            <input type="text" class="commentaire" placeholder="Commentaire (facultatif) : ce qui ne va pas, ce qu’il faudrait"
              [ngModel]="notes()[p.id]?.commentaire ?? ''" (ngModelChange)="commenter(p.id, $event)" (blur)="enregistrer(p.id)" />
            @if (etat()[p.id]; as e) { <span class="aide etat">{{ e }}</span> }
          </div>
        }

        @if (synthese().length) {
          <div class="carte">
            <h2>Comparatif des modèles <span class="aide">tous les calendriers notés de l’espace</span></h2>
            <div class="tableau">
              <table>
                <thead><tr><th>Modèle</th><th>Calendriers</th><th>Notées</th><th>Moyenne</th><th>Envoyables</th>
                  @for (k of criteres; track k.cle) { <th>{{ k.nom }}</th> }<th>Équilibre</th></tr></thead>
                <tbody>
                  @for (l of synthese(); track l.modele) {
                    <tr [class.actuel]="l.modele === evaluation()?.modele">
                      <td>{{ l.modele }}</td><td>{{ l.calendriers }}</td><td>{{ l.moyennes.evaluees }}</td>
                      <td><strong>{{ l.moyennes.globale ?? '–' }}</strong></td>
                      <td>{{ l.moyennes.partEnvoyables != null ? l.moyennes.partEnvoyables + ' %' : '–' }}</td>
                      @for (k of criteres; track k.cle) { <td>{{ moyenne(l.moyennes, k.cle) ?? '–' }}</td> }
                      <td>{{ l.equilibre ?? '–' }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            <p class="aide">Pour une comparaison juste : mêmes clients, même période, mêmes réglages ; seul le modèle change.</p>
          </div>
        }
      } @else if (!erreur()) { <p class="chargement">Chargement…</p> }
    </div>
  `,
  styles: `
    .resume .chiffres { display: flex; gap: 12px 32px; flex-wrap: wrap; }
    .resume .chiffres div { display: flex; flex-direction: column; max-width: 220px; }
    .grand { font-size: 1.9rem; font-weight: 700; color: var(--vert); line-height: 1.1; }
    .criteres-moy { display: flex; gap: 18px; flex-wrap: wrap; margin-top: 12px; font-size: .9rem; color: var(--gris); }
    .criteres-moy strong { color: var(--texte, #1a1a1a); }
    hr { border: none; border-top: 1px solid var(--gris-clair); margin: 16px 0; }
    .carte + .carte, .carte + details, details + .carte { margin-top: 14px; }
    .legende-criteres summary { cursor: pointer; font-weight: 600; }
    .legende-criteres p { margin: 6px 0; font-size: .9rem; }
    .pub { border-left: 4px solid transparent; }
    .pub.notee { border-left-color: var(--vert); }
    .tete { display: flex; justify-content: space-between; gap: 12px; }
    .idee { margin: 6px 0; }
    .idee .pastille { margin-left: 8px; font-size: .72rem; }
    .legende { white-space: pre-line; color: #444; font-size: .92rem; margin: 6px 0; cursor: pointer;
      display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
    .legende.ouverte { display: block; }
    .grille { display: grid; grid-template-columns: repeat(auto-fill, minmax(190px, 1fr)); gap: 10px 18px; margin: 12px 0 10px; }
    .critere .nom { display: block; font-size: .82rem; font-weight: 600; margin-bottom: 4px; }
    .echelle { display: inline-flex; border: 1px solid var(--gris-clair); border-radius: 8px; overflow: hidden; }
    .echelle button { border: none; background: #fff; width: 34px; height: 32px; cursor: pointer; font-weight: 600; color: var(--gris);
      border-left: 1px solid var(--gris-clair); }
    .echelle button:first-child { border-left: none; }
    .echelle button:hover { background: var(--vert-clair, #e8f5e9); }
    .echelle button.choisi { background: var(--vert); color: #fff; }
    .commentaire { width: 100%; }
    .etat { display: block; margin-top: 4px; }
    .tableau { overflow-x: auto; }
    table { width: 100%; border-collapse: collapse; font-size: .85rem; }
    th, td { text-align: left; padding: 6px 6px; border-bottom: 1px solid var(--gris-clair); white-space: nowrap; }
    th { color: var(--gris); font-weight: 600; }
    tr.actuel td { background: var(--vert-clair, #e8f5e9); }
  `,
})
export class EvaluationComponent implements OnInit {
  private api = inject(ApiService);
  id = input.required<string>();
  calendrier = signal<Calendrier | null>(null);
  evaluation = signal<EvaluationCalendrier | null>(null);
  synthese = signal<LigneSynthese[]>([]);
  notes = signal<Partial<Record<string, NotesPublication>>>({});
  etat = signal<Record<string, string>>({});
  ouvertes = signal(new Set<string>());
  erreur = signal('');
  criteres = CRITERES;
  libelles = LIBELLES_RESEAU;
  equilibre: number | null = null;
  avis = '';

  notee = (id: string) => this.criteres.some(k => this.notes()[id]?.[k.cle] != null);

  ngOnInit() {
    this.api.calendrier(this.id()).subscribe({ next: c => this.calendrier.set(c), error: e => this.erreur.set(messageErreur(e)) });
    this.api.evaluation(this.id()).subscribe({
      next: e => {
        this.appliquer(e);
        this.notes.set(Object.fromEntries(e.notes.map(n => [n.publicationId!, n])));
      },
      error: e => this.erreur.set(messageErreur(e)),
    });
    this.chargerSynthese();
  }

  note(id: string, k: Critere) { return this.notes()[id]?.[k] ?? null; }

  moyenne(m: MoyennesEvaluation, k: Critere) { return m[k] ?? null; }

  basculer(id: string) {
    this.ouvertes.update(s => { const n = new Set(s); n.has(id) ? n.delete(id) : n.add(id); return n; });
  }

  choisir(id: string, k: Critere, v: number) {
    const actuelle = this.note(id, k);
    this.maj(id, { [k]: actuelle === v ? null : v });
    this.enregistrer(id);
  }

  commenter(id: string, texte: string) { this.maj(id, { commentaire: texte }); }

  enregistrer(id: string) {
    const n = this.notes()[id] ?? {};
    this.etat.update(e => ({ ...e, [id]: 'Enregistrement…' }));
    this.api.noter(id, {
      specificite: n.specificite ?? null, exactitude: n.exactitude ?? null, contexte: n.contexte ?? null,
      faisabilite: n.faisabilite ?? null, pret: n.pret ?? null, commentaire: n.commentaire ?? null,
    }).subscribe({
      next: () => { this.etat.update(e => ({ ...e, [id]: 'Enregistré' })); this.rafraichir(); },
      error: err => this.etat.update(e => ({ ...e, [id]: messageErreur(err) })),
    });
  }

  enregistrerAvis() {
    this.api.avisCalendrier(this.id(), this.equilibre, this.avis).subscribe({
      next: e => { this.appliquer(e); this.chargerSynthese(); },
      error: err => this.erreur.set(messageErreur(err)),
    });
  }

  private maj(id: string, champs: Partial<NotesPublication>) {
    this.notes.update(t => ({ ...t, [id]: { ...(t[id] ?? {}), ...champs } }));
  }

  private appliquer(e: EvaluationCalendrier) {
    this.evaluation.set(e);
    this.equilibre = e.noteEquilibre ?? null;
    this.avis = e.avisGlobal ?? '';
  }

  /** Moyennes recalculées par le serveur après chaque note (léger : une requête). */
  private minuteur?: ReturnType<typeof setTimeout>;
  private rafraichir() {
    clearTimeout(this.minuteur);
    this.minuteur = setTimeout(() => {
      this.api.evaluation(this.id()).subscribe({ next: e => this.evaluation.set(e), error: () => {} });
      this.chargerSynthese();
    }, 600);
  }

  private chargerSynthese() {
    this.api.syntheseEvaluations().subscribe({ next: l => this.synthese.set(l), error: () => {} });
  }
}
