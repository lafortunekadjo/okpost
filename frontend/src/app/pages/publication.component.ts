import { Component, inject, input, signal, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService, messageErreur } from '../core/api.service';
import {
  BriefVisuel, LIBELLES_FORMAT, LIBELLES_RESEAU, LIBELLES_STATUT, Publication, RESEAUX, ScenarioVideo, StatutPublication,
} from '../core/models';
import { PucesComponent } from '../shared/puces.component';

const DECISIONS: Partial<Record<string, string>> = { OK: 'Validée', A_MODIFIER: 'Modification demandée', MODIFIEE: 'Légende modifiée par le client' };

@Component({
  selector: 'app-publication',
  imports: [FormsModule, RouterLink, DatePipe, PucesComponent],
  template: `
    <div class="page page-etroite">
      @if (erreur()) { <div class="erreur">{{ erreur() }}</div> }
      @if (p(); as pub) {
        <div class="entete-page">
          <div>
            <a [routerLink]="['/calendriers', pub.calendrierId]" class="aide">← Calendrier</a>
            <h1>{{ pub.date | date: 'EEEE d MMMM' }}</h1>
            <p class="sous-titre"><span class="reseau reseau-{{ pub.reseau }}"></span>{{ libellesReseau[pub.reseau] }} · {{ libellesFormat[pub.format] }}
              @if (pub.pilier) { · {{ pub.pilier }} }</p>
          </div>
          <span class="pastille statut-{{ pub.statut }}">{{ libellesStatut[pub.statut] }}</span>
        </div>

        @if (pub.retours?.length) {
          <div class="carte retours">
            <h2>Retours du client</h2>
            @for (r of pub.retours; track $index) {
              <div class="retour">
                <strong>{{ decisions[r.decision] ?? r.decision }}</strong>
                <span class="aide"> · {{ r.date | date: 'd MMM, HH:mm' }}</span>
                @if (r.commentaire) { <p class="pre">« {{ r.commentaire }} »</p> }
                @if (r.legendeOrigine) {
                  <details><summary class="aide">Voir la légende d'origine</summary><p class="pre aide">{{ r.legendeOrigine }}</p></details>
                }
              </div>
            }
          </div>
        }

        @if (message()) { <div class="succes">{{ message() }}</div> }

        <form class="carte" (ngSubmit)="enregistrer()">
          <div class="ligne-champs">
            <div class="champ"><label>Date</label><input type="date" name="date" [(ngModel)]="pub.date" /></div>
            <div class="champ"><label>Réseau</label>
              <select name="reseau" [(ngModel)]="pub.reseau">@for (r of reseaux; track r) { <option [value]="r">{{ libellesReseau[r] }}</option> }</select></div>
            <div class="champ"><label>Format</label>
              <select name="format" [(ngModel)]="pub.format">@for (f of formats; track f) { <option [value]="f">{{ libellesFormat[f] }}</option> }</select></div>
            <div class="champ"><label>Statut</label>
              <select name="statut" [(ngModel)]="pub.statut">@for (s of statuts; track s) { <option [value]="s">{{ libellesStatut[s] }}</option> }</select></div>
          </div>
          <div class="champ"><label>Idée</label><textarea name="idee" rows="2" [(ngModel)]="pub.idee"></textarea></div>

          <div class="champ">
            <div class="ligne"><label class="espace">Légende <span class="aide">{{ (pub.legende || '').length }} caractères</span></label>
              <button type="button" class="bouton secondaire petit" (click)="copier(texteAPublier(pub), 'legende')">{{ copie() === 'legende' ? 'Copié' : 'Copier légende + hashtags' }}</button></div>
            <textarea name="legende" rows="8" [(ngModel)]="pub.legende"></textarea>
          </div>
          <div class="champ"><label>Hashtags</label><app-puces [(valeurs)]="hashtags" placeholder="#Douala puis Entrée" /></div>
          <div class="champ"><label>Appel à l'action</label><input type="text" name="cta" [(ngModel)]="pub.cta" /></div>

          @if (pub.format === 'VIDEO') {
            <h2>Scénario vidéo</h2>
            <div class="ligne-champs">
              <div class="champ"><label>Titre</label><input type="text" name="sc-titre" [(ngModel)]="scenario.titre" /></div>
              <div class="champ"><label>Durée totale (s)</label><input type="number" name="sc-duree" [(ngModel)]="scenario.dureeTotaleS" /></div>
            </div>
            <div class="champ"><label>Accroche (2 premières secondes)</label><input type="text" name="sc-accroche" [(ngModel)]="scenario.accroche" /></div>
            @for (plan of scenario.plans; track $index) {
              <div class="plan">
                <div class="ligne"><strong>Plan {{ plan.numero }}</strong>
                  <input type="number" [name]="'p-duree-' + $index" [(ngModel)]="plan.dureeS" style="width:80px" /> <span class="aide">s</span>
                  <span class="espace"></span>
                  <button type="button" class="bouton danger petit" (click)="retirerPlan($index)">Retirer</button></div>
                <div class="ligne-champs">
                  <div class="champ"><label>Image</label><textarea rows="2" [name]="'p-img-' + $index" [(ngModel)]="plan.image"></textarea></div>
                  <div class="champ"><label>Texte à l'écran</label><textarea rows="2" [name]="'p-txt-' + $index" [(ngModel)]="plan.texteEcran"></textarea></div>
                </div>
                <div class="ligne-champs">
                  <div class="champ"><label>Voix off</label><textarea rows="2" [name]="'p-voix-' + $index" [(ngModel)]="plan.voixOff"></textarea></div>
                  <div class="champ"><label>Son</label><input type="text" [name]="'p-son-' + $index" [(ngModel)]="plan.son" /></div>
                </div>
              </div>
            }
            <button type="button" class="bouton secondaire petit" (click)="ajouterPlan()">+ Ajouter un plan</button>
            <div class="champ" style="margin-top:14px"><label>Matériel</label><app-puces [(valeurs)]="materiel" /></div>
          } @else {
            <h2>Brief visuel</h2>
            <div class="champ"><label>Pour le graphiste</label><textarea name="b-desc" rows="2" [(ngModel)]="brief.description"></textarea></div>
            <div class="champ">
              <div class="ligne"><label class="espace">Prompt d'image <span class="aide">à coller dans Canva, Ideogram, Midjourney…</span></label>
                <button type="button" class="bouton secondaire petit" (click)="copier(brief.promptImage || '', 'prompt')">{{ copie() === 'prompt' ? 'Copié' : 'Copier le prompt' }}</button></div>
              <textarea name="b-prompt" rows="4" class="mono" [(ngModel)]="brief.promptImage"></textarea>
            </div>
            <div class="champ"><label>Format d'image</label>
              <select name="b-ratio" [(ngModel)]="brief.ratio" style="max-width:200px">
                <option value="1:1">Carré 1:1</option><option value="4:5">Portrait 4:5</option><option value="9:16">Vertical 9:16</option>
              </select></div>
            @if (pub.format === 'CARROUSEL') {
              <h3>Textes du carrousel</h3>
              @for (t of brief.textesCarrousel; track $index) {
                <div class="plan">
                  <div class="ligne"><strong>Visuel {{ $index + 1 }}</strong><span class="espace"></span>
                    <button type="button" class="bouton danger petit" (click)="brief.textesCarrousel!.splice($index, 1)">Retirer</button></div>
                  <div class="champ"><label>Titre</label><input type="text" [name]="'c-titre-' + $index" [(ngModel)]="t.titre" /></div>
                  <div class="champ"><label>Texte</label><textarea rows="2" [name]="'c-texte-' + $index" [(ngModel)]="t.texte"></textarea></div>
                </div>
              }
              <button type="button" class="bouton secondaire petit" (click)="ajouterVisuel()">+ Ajouter un visuel</button>
            }
          }

          <div class="actions">
            <span class="espace"></span>
            <button class="bouton" [disabled]="envoi()">{{ envoi() ? 'Enregistrement…' : 'Enregistrer' }}</button>
          </div>
        </form>

        <div class="carte">
          <h2>Régénérer avec l'IA</h2>
          <div class="champ"><label>Consigne <span class="aide">facultative</span></label>
            <input type="text" [(ngModel)]="consigne" placeholder="Ex. plus court, ton plus festif, mettre en avant la livraison gratuite" /></div>
          <div class="ligne">
            <button class="bouton secondaire" [disabled]="regen()" (click)="regenerer('legende')">La légende</button>
            <button class="bouton secondaire" [disabled]="regen()" (click)="regenerer('cta')">L'appel à l'action</button>
            <button class="bouton secondaire" [disabled]="regen()" (click)="regenerer('tout')">Toute la publication</button>
            @if (regen()) { <span class="aide">Génération…</span> }
          </div>
          <p class="aide" style="margin-top:8px">Les modifications non enregistrées seront remplacées.</p>
        </div>
      } @else if (!erreur()) {
        <p class="chargement">Chargement…</p>
      }
    </div>
  `,
  styles: `
    .retours { border-left: 4px solid var(--orange); }
    .retour { padding: 8px 0; border-top: 1px solid var(--gris-clair); }
    .retour:first-of-type { border-top: none; }
    .retour p { margin: 6px 0 0; }
    .plan { border: 1px solid var(--gris-clair); border-radius: 10px; padding: 12px; margin-bottom: 10px; }
    .actions { display: flex; border-top: 1px solid var(--gris-clair); margin-top: 20px; padding-top: 16px; }
    h2 { margin-top: 20px; }
    .carte > h2:first-child { margin-top: 0; }
  `,
})
export class PublicationComponent implements OnInit {
  private api = inject(ApiService);
  id = input.required<string>();

  p = signal<Publication | null>(null);
  envoi = signal(false);
  regen = signal(false);
  erreur = signal('');
  message = signal('');
  copie = signal('');
  consigne = '';

  hashtags: string[] = [];
  materiel: string[] = [];
  brief: BriefVisuel = {};
  scenario: ScenarioVideo = { plans: [] };

  reseaux = RESEAUX;
  formats = Object.keys(LIBELLES_FORMAT);
  statuts = Object.keys(LIBELLES_STATUT) as StatutPublication[];
  libellesReseau = LIBELLES_RESEAU;
  libellesFormat = LIBELLES_FORMAT;
  libellesStatut = LIBELLES_STATUT;
  decisions = DECISIONS;

  ngOnInit() {
    this.api.publication(this.id()).subscribe({ next: p => this.afficher(p), error: e => this.erreur.set(messageErreur(e)) });
  }

  private afficher(p: Publication) {
    this.p.set({ ...p });
    this.hashtags = [...(p.hashtags ?? [])];
    this.brief = structuredClone(p.brief ?? { textesCarrousel: [] });
    this.brief.textesCarrousel ??= [];
    this.scenario = structuredClone(p.scenario ?? { plans: [] });
    this.scenario.plans ??= [];
    this.materiel = [...(this.scenario.materiel ?? [])];
  }

  texteAPublier(p: Publication) {
    return [p.legende ?? '', p.cta ?? '', this.hashtags.join(' ')].filter(x => x.trim()).join('\n\n');
  }

  ajouterPlan() {
    const n = (this.scenario.plans?.length ?? 0) + 1;
    this.scenario.plans!.push({ numero: n, dureeS: 3 });
  }

  retirerPlan(i: number) {
    this.scenario.plans!.splice(i, 1);
    this.scenario.plans!.forEach((x, k) => (x.numero = k + 1));
  }

  ajouterVisuel() {
    const n = this.brief.textesCarrousel!.length + 1;
    this.brief.textesCarrousel!.push({ visuel: n, titre: '', texte: '' });
  }

  enregistrer() {
    const p = this.p();
    if (!p) return;
    this.envoi.set(true);
    this.erreur.set('');
    const patch: Partial<Publication> = {
      date: p.date, reseau: p.reseau, format: p.format, statut: p.statut, idee: p.idee, legende: p.legende ?? '',
      cta: p.cta ?? '', hashtags: this.hashtags, version: p.version,
    };
    if (p.format === 'VIDEO') {
      patch.scenario = { ...this.scenario, materiel: this.materiel };
    } else {
      this.brief.textesCarrousel?.forEach((t, k) => (t.visuel = k + 1));
      patch.brief = this.brief;
    }
    this.api.modifierPublication(p.id, patch).subscribe({
      next: maj => {
        this.afficher(maj);
        this.envoi.set(false);
        this.message.set('Publication enregistrée.');
        setTimeout(() => this.message.set(''), 2500);
      },
      error: e => { this.erreur.set(messageErreur(e)); this.envoi.set(false); },
    });
  }

  regenerer(champ: 'legende' | 'cta' | 'tout') {
    const p = this.p();
    if (!p) return;
    this.regen.set(true);
    this.erreur.set('');
    this.api.regenerer(p.id, champ, this.consigne).subscribe({
      next: maj => { this.afficher(maj); this.regen.set(false); },
      error: e => { this.erreur.set(messageErreur(e)); this.regen.set(false); },
    });
  }

  copier(texte: string, cle: string) {
    navigator.clipboard?.writeText(texte).then(() => {
      this.copie.set(cle);
      setTimeout(() => this.copie.set(''), 2000);
    });
  }
}
