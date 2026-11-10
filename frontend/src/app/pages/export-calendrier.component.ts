import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService, messageErreur } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import {
  Calendrier, Client, LIBELLES_FORMAT, LIBELLES_RESEAU, LIBELLES_STATUT, Publication,
} from '../core/models';

interface Semaine { titre: string; publications: Publication[]; }
interface Compte { libelle: string; nombre: number; }

const OBJECTIFS: Partial<Record<string, string>> = {
  NOTORIETE: 'Notoriété', ENGAGEMENT: 'Engagement', VENTE: 'Vente', CONFIANCE: 'Confiance', RECRUTEMENT: 'Recrutement',
};
const DECISIONS: Partial<Record<string, string>> = { OK: 'Validée', A_MODIFIER: 'Modification demandée', MODIFIEE: 'Légende modifiée par le client' };

/** Rapport imprimable du calendrier, aux couleurs du client, à enregistrer en PDF depuis le navigateur. */
@Component({
  selector: 'app-export-calendrier',
  imports: [DatePipe, FormsModule, RouterLink],
  template: `
    @if (erreur()) { <div class="page"><div class="erreur">{{ erreur() }}</div></div> }
    @if (cal(); as c) {
      <div class="outils no-print">
        @if (!jeton()) {
          <a [routerLink]="['/calendriers', c.id]" class="bouton secondaire petit">← Retour au calendrier</a>
          <span class="pastille">{{ partage() ? 'Version client' : 'Version évaluation' }}</span>
        } @else {
          <strong>Calendrier éditorial · {{ c.clientNom }}</strong>
        }
        @if (!partage()) {
          <label><input type="checkbox" [(ngModel)]="opt.details" /> Fiches détaillées</label>
          <label><input type="checkbox" [(ngModel)]="opt.prompts" /> Prompts d'image</label>
          <label><input type="checkbox" [(ngModel)]="opt.visuels" /> Visuels</label>
          <label><input type="checkbox" [(ngModel)]="opt.retours" /> Retours du client</label>
          <label><input type="checkbox" [(ngModel)]="opt.avis" /> Zone d'avis pour les experts</label>
        } @else if (!jeton()) {
          <label><input type="checkbox" [(ngModel)]="opt.details" /> Fiches détaillées</label>
          <label><input type="checkbox" [(ngModel)]="opt.visuels" /> Visuels</label>
        }
        <span class="espace"></span>
        <button class="bouton" (click)="imprimer()">Télécharger en PDF</button>
        <span class="aide astuce">Dans la fenêtre qui s'ouvre, choisis « Enregistrer au format PDF » (sur téléphone : Partager → Imprimer → PDF).</span>
      </div>

      <div class="rapport" [style.--c1]="c1()" [style.--c2]="c2()">

        <!-- Couverture -->
        <section class="couverture">
          <div class="bande">
            @if (logo()) { <div class="pastille-logo"><img [src]="logo()" alt="" /></div> }
            <div>
              <p class="sur-titre">Calendrier éditorial</p>
              <h1>{{ c.clientNom }}</h1>
              <p class="periode">Du {{ c.debut | date: 'd MMMM' }} au {{ c.fin | date: 'd MMMM y' }}</p>
            </div>
          </div>

          <div class="chiffres">
            <div><strong>{{ c.publications.length }}</strong><span>publications</span></div>
            <div><strong>{{ c.reseaux.length }}</strong><span>réseau{{ c.reseaux.length > 1 ? 'x' : '' }}</span></div>
            <div><strong>{{ semaines().length }}</strong><span>semaine{{ semaines().length > 1 ? 's' : '' }}</span></div>
            <div><strong>{{ nbVideos() }}</strong><span>vidéo{{ nbVideos() > 1 ? 's' : '' }}</span></div>
          </div>

          @if (c.strategie) {
            <div class="bloc">
              <h2>Stratégie de la période</h2>
              <p>{{ c.strategie }}</p>
            </div>
          }
          @if (c.objectifs || c.tempsForts) {
            <div class="deux-colonnes">
              @if (c.objectifs) { <div class="bloc"><h3>Objectifs</h3><p class="pre">{{ c.objectifs }}</p></div> }
              @if (c.tempsForts) { <div class="bloc"><h3>Temps forts</h3><p class="pre">{{ c.tempsForts }}</p></div> }
            </div>
          }
          @if (c.piliers?.length) {
            <div class="bloc">
              <h3>Piliers de contenu</h3>
              <div class="puces">@for (p of c.piliers; track p) { <span class="puce-marque">{{ p }}</span> }</div>
            </div>
          }

          <div class="trois-colonnes">
            <div class="bloc"><h3>Par réseau</h3>
              @for (x of parReseau(); track x.libelle) { <div class="barre-ligne"><span>{{ x.libelle }}</span>
                <span class="jauge"><span [style.width.%]="(x.nombre / max(parReseau())) * 100"></span></span><b>{{ x.nombre }}</b></div> }
            </div>
            <div class="bloc"><h3>Par format</h3>
              @for (x of parFormat(); track x.libelle) { <div class="barre-ligne"><span>{{ x.libelle }}</span>
                <span class="jauge"><span [style.width.%]="(x.nombre / max(parFormat())) * 100"></span></span><b>{{ x.nombre }}</b></div> }
            </div>
            <div class="bloc"><h3>Par pilier</h3>
              @for (x of parPilier(); track x.libelle) { <div class="barre-ligne"><span>{{ x.libelle }}</span>
                <span class="jauge"><span [style.width.%]="(x.nombre / max(parPilier())) * 100"></span></span><b>{{ x.nombre }}</b></div> }
            </div>
          </div>

          <p class="mention">Document édité le {{ aujourdhui | date: 'd MMMM y' }}{{ auteur() ? ' par ' + auteur() : '' }} · Préparé avec OkPost</p>
        </section>

        <!-- Vue d'ensemble -->
        <section class="ensemble">
          <h2 class="titre-section">Vue d'ensemble</h2>
          @for (s of semaines(); track s.titre) {
            <h3 class="semaine">{{ s.titre }}</h3>
            <table>
              <thead><tr><th>Date</th><th>Réseau</th><th>Format</th><th>Pilier</th><th>Idée</th>@if (!partage()) { <th>Statut</th> }</tr></thead>
              <tbody>
                @for (p of s.publications; track p.id) {
                  <tr>
                    <td class="nowrap">{{ p.date | date: 'EEE d MMM' }}</td>
                    <td class="nowrap"><span class="reseau reseau-{{ p.reseau }}"></span>{{ reseaux[p.reseau] ?? p.reseau }}</td>
                    <td>{{ formats[p.format] ?? p.format }}</td>
                    <td>{{ p.pilier }}</td>
                    <td>{{ p.idee }}</td>
                    @if (!partage()) { <td class="nowrap"><span class="pastille statut-{{ p.statut }}">{{ statuts[p.statut] }}</span></td> }
                  </tr>
                }
              </tbody>
            </table>
          }
        </section>

        <!-- Fiches détaillées -->
        @if (opt.details) {
          <section class="details">
            <h2 class="titre-section">Publications en détail</h2>
            @for (p of c.publications; track p.id; let i = $index) {
              <article class="fiche">
                <header>
                  <span class="numero">{{ i + 1 }}</span>
                  <strong>{{ p.date | date: 'EEEE d MMMM' }}</strong>
                  <span><span class="reseau reseau-{{ p.reseau }}"></span>{{ reseaux[p.reseau] ?? p.reseau }} · {{ formats[p.format] ?? p.format }}</span>
                  @if (p.pilier) { <span class="puce-marque petite">{{ p.pilier }}</span> }
                  @if (p.objectif) { <span class="discret">Objectif : {{ objectifs[p.objectif] ?? p.objectif }}</span> }
                </header>

                <p class="idee">{{ p.idee }}</p>

                <div class="corps" [class.avec-visuel]="opt.visuels && (apercu(p) || video(p))">
                  @if (opt.visuels && apercu(p); as img) {
                    <img class="visuel" [src]="img" alt="Visuel" />
                  } @else if (opt.visuels && video(p)) {
                    <a class="visuel video" [href]="video(p)" target="_blank" rel="noopener"><span>▶</span>Vidéo jointe<small>voir en ligne</small></a>
                  }
                  <div>
                    @if (p.legende) { <h4>Légende</h4><p class="pre">{{ p.legende }}</p> }
                    @if (p.cta) { <h4>Appel à l'action</h4><p>{{ p.cta }}</p> }
                    @if (p.hashtags?.length) { <p class="hashtags">{{ p.hashtags!.join(' ') }}</p> }
                  </div>
                </div>

                @if (p.format === 'VIDEO' && p.scenario) {
                  <h4>Scénario vidéo · {{ p.scenario.titre }} · {{ p.scenario.dureeTotaleS }} s</h4>
                  @if (p.scenario.accroche) { <p><em>Accroche :</em> {{ p.scenario.accroche }}</p> }
                  @if (p.scenario.plans?.length) {
                    <table class="plans">
                      <thead><tr><th>#</th><th>Durée</th><th>Image</th><th>Texte à l'écran</th><th>Voix off</th></tr></thead>
                      <tbody>
                        @for (pl of p.scenario.plans; track pl.numero) {
                          <tr><td>{{ pl.numero }}</td><td class="nowrap">{{ pl.dureeS }} s</td><td>{{ pl.image }}</td><td>{{ pl.texteEcran }}</td><td>{{ pl.voixOff }}</td></tr>
                        }
                      </tbody>
                    </table>
                  }
                  @if (p.scenario.materiel?.length) { <p class="discret">Matériel : {{ p.scenario.materiel!.join(', ') }}</p> }
                } @else if (p.brief) {
                  @if (p.brief.description) { <h4>Brief visuel</h4><p>{{ p.brief.description }}</p> }
                  @if (p.brief.textesCarrousel?.length) {
                    <h4>Textes du carrousel</h4>
                    <ol class="carrousel">
                      @for (t of p.brief.textesCarrousel; track $index) { <li><strong>{{ t.titre }}</strong> — {{ t.texte }}</li> }
                    </ol>
                  }
                  @if (!partage() && opt.prompts && p.brief.promptImage) {
                    <h4>Prompt d'image</h4><p class="prompt">{{ p.brief.promptImage }}</p>
                  }
                }

                @if (!partage() && opt.retours && p.retours?.length) {
                  <div class="retours">
                    @for (r of p.retours; track $index) {
                      <p><strong>{{ decisions[r.decision] ?? r.decision }}</strong> · {{ r.date | date: 'd MMM' }}
                        @if (r.commentaire) { — « {{ r.commentaire }} » }</p>
                    }
                  </div>
                }

                @if (!partage() && opt.avis) {
                  <div class="avis">
                    <div class="criteres">
                      @for (critere of criteres; track critere) {
                        <span>{{ critere }} <span class="cases">@for (n of [1, 2, 3, 4, 5]; track n) { <i>{{ n }}</i> }</span></span>
                      }
                    </div>
                    <div class="lignes"><span>Commentaire de l'expert :</span></div>
                  </div>
                }
              </article>
            }
          </section>
        }

        @if (!partage() && opt.avis) {
          <section class="synthese">
            <h2 class="titre-section">Avis global</h2>
            <div class="avis grand">
              <div class="criteres">
                @for (critere of criteresGlobaux; track critere) {
                  <span>{{ critere }} <span class="cases">@for (n of [1, 2, 3, 4, 5]; track n) { <i>{{ n }}</i> }</span></span>
                }
              </div>
              <div class="lignes haute"><span>Points forts :</span></div>
              <div class="lignes haute"><span>Points à améliorer :</span></div>
              <div class="signature"><span>Nom de l'expert :</span><span>Date :</span></div>
            </div>
          </section>
        }
      </div>
    } @else if (!erreur()) {
      <p class="chargement page">Préparation du document…</p>
    }
  `,
  styles: `
    :host { display: block; background: #e5e7eb; min-height: 100vh; }
    .outils { position: sticky; top: 0; z-index: 5; display: flex; gap: 14px; flex-wrap: wrap; align-items: center;
      background: #fff; border-bottom: 1px solid var(--gris-clair); padding: 10px 16px; font-size: .85rem; }
    .outils label { display: inline-flex; gap: 6px; align-items: center; margin: 0; font-weight: 500; }
    .astuce { flex-basis: 100%; text-align: right; }

    .rapport { --c1: #15803d; --c2: #166534; --c1-clair: color-mix(in srgb, var(--c1) 10%, white);
      --texte-marque: color-mix(in srgb, var(--c1) 78%, black);
      max-width: 210mm; margin: 20px auto; background: #fff; color: #111827; font-size: 10.5pt; line-height: 1.45;
      box-shadow: 0 4px 24px rgba(0,0,0,.12); }
    section { padding: 14mm 12mm; }
    section + section { border-top: 1px dashed #d1d5db; }

    .couverture { padding: 0 0 12mm; }
    .bande { display: flex; gap: 18px; align-items: center; padding: 16mm 12mm 12mm;
      background: linear-gradient(135deg, var(--c1), var(--c2)); color: #fff; }
    .pastille-logo { background: #fff; border-radius: 14px; padding: 10px 14px; box-shadow: 0 2px 8px rgba(0,0,0,.15); }
    .pastille-logo img { display: block; max-height: 22mm; max-width: 55mm; object-fit: contain; }
    .sur-titre { margin: 0; text-transform: uppercase; letter-spacing: .12em; font-size: 9pt; opacity: .85; font-weight: 600; }
    .bande h1 { font-size: 26pt; margin: 2px 0; color: #fff; line-height: 1.1; }
    .periode { margin: 0; font-size: 12pt; opacity: .95; }
    .couverture > .chiffres, .couverture > .bloc, .couverture > .deux-colonnes, .couverture > .trois-colonnes, .couverture > .mention { margin-left: 12mm; margin-right: 12mm; }
    .chiffres { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-top: -8mm; margin-bottom: 8mm; }
    .chiffres div { background: #fff; border-radius: 10px; padding: 10px 12px; box-shadow: 0 2px 10px rgba(0,0,0,.1);
      display: flex; flex-direction: column; border-top: 4px solid var(--c1); }
    .chiffres strong { font-size: 20pt; color: var(--c1); line-height: 1; }
    .chiffres span { color: #6b7280; font-size: 9pt; margin-top: 4px; }
    .bloc { margin-bottom: 6mm; }
    .bloc h2, .bloc h3 { color: var(--texte-marque); }
    h2 { font-size: 14pt; margin: 0 0 6px; }
    h3 { font-size: 11pt; margin: 0 0 6px; }
    h4 { font-size: 9pt; text-transform: uppercase; letter-spacing: .06em; color: var(--texte-marque); margin: 10px 0 3px; break-after: avoid; }
    .deux-colonnes { display: grid; grid-template-columns: 1fr 1fr; gap: 10mm; }
    .trois-colonnes { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8mm; }
    .puce-marque { display: inline-block; background: var(--c1-clair); color: var(--texte-marque); border: 1px solid color-mix(in srgb, var(--c1) 35%, white);
      border-radius: 999px; padding: 2px 10px; font-size: 9pt; font-weight: 600; }
    .puce-marque.petite { font-size: 8pt; padding: 1px 8px; }
    .barre-ligne { display: grid; grid-template-columns: 1fr 60px 18px; gap: 6px; align-items: center; font-size: 9pt; margin-bottom: 3px; }
    .jauge { height: 6px; background: #f3f4f6; border-radius: 999px; overflow: hidden; }
    .jauge span { display: block; height: 100%; background: var(--c1); }
    .barre-ligne b { text-align: right; }
    .mention { color: #6b7280; font-size: 8.5pt; margin-top: 8mm; }

    .titre-section { color: var(--texte-marque); border-left: 5px solid var(--c1); padding-left: 10px; margin-bottom: 10px; }
    .semaine { margin: 14px 0 6px; color: #374151; font-size: 10pt; break-after: avoid; }
    table { width: 100%; border-collapse: collapse; font-size: 9pt; }
    th { text-align: left; background: var(--c1-clair); color: var(--texte-marque); font-weight: 700; padding: 6px; border-bottom: 2px solid var(--c1); }
    td { padding: 6px; border-bottom: 1px solid #e5e7eb; vertical-align: top; }
    tr { break-inside: avoid; }
    .nowrap { white-space: nowrap; }

    .fiche { border: 1px solid #e5e7eb; border-left: 4px solid var(--c1); border-radius: 8px; padding: 10px 14px; margin-bottom: 10px; break-inside: avoid; }
    .fiche header { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; font-size: 9.5pt; }
    .numero { background: var(--c1); color: #fff; border-radius: 999px; min-width: 22px; height: 22px; display: inline-flex;
      align-items: center; justify-content: center; font-weight: 700; font-size: 8.5pt; }
    .discret { color: #6b7280; font-size: 8.5pt; }
    .idee { font-weight: 600; font-size: 11pt; margin: 8px 0 4px; }
    .corps.avec-visuel { display: grid; grid-template-columns: 45mm 1fr; gap: 12px; }
    .visuel { width: 45mm; border-radius: 6px; margin-top: 10px; }
    .visuel.video { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2px; height: 45mm;
      background: var(--c1-clair); color: var(--texte-marque); font-weight: 700; text-decoration: none; font-size: 9pt; }
    .visuel.video span { font-size: 22pt; line-height: 1; }
    .visuel.video small { font-weight: 400; color: #6b7280; }
    .hashtags { color: var(--texte-marque); font-size: 9pt; }
    .prompt { font-family: ui-monospace, Menlo, monospace; font-size: 8pt; background: #f9fafb; border-radius: 6px; padding: 6px 8px; color: #374151; }
    .carrousel { margin: 0; padding-left: 18px; }
    .plans { font-size: 8.5pt; margin-top: 4px; }
    .retours { background: #fff7ed; border-radius: 6px; padding: 4px 10px; margin-top: 8px; font-size: 9pt; }
    .retours p { margin: 4px 0; }

    .avis { margin-top: 10px; border-top: 1px dashed #d1d5db; padding-top: 8px; font-size: 8.5pt; color: #374151; }
    .criteres { display: flex; gap: 14px; flex-wrap: wrap; margin-bottom: 6px; }
    .cases { display: inline-flex; gap: 3px; margin-left: 4px; }
    .cases i { font-style: normal; width: 15px; height: 15px; border: 1px solid #9ca3af; border-radius: 3px; display: inline-flex;
      align-items: center; justify-content: center; font-size: 7pt; color: #9ca3af; }
    .lignes { min-height: 16mm; background-image: repeating-linear-gradient(transparent, transparent 7mm, #d1d5db 7mm, #d1d5db calc(7mm + 1px)); }
    .lignes.haute { min-height: 36mm; margin-bottom: 4mm; }
    .avis.grand { font-size: 10pt; }
    .signature { display: flex; gap: 40mm; margin-top: 6mm; }
    .synthese { break-before: page; }

    @media screen and (max-width: 640px) {
      .rapport { margin: 0; font-size: 10pt; }
      section { padding: 6mm 4mm; }
      .bande { flex-direction: column; align-items: flex-start; padding: 8mm 5mm 12mm; }
      .bande h1 { font-size: 20pt; }
      .pastille-logo img { max-height: 14mm; }
      .couverture > .chiffres, .couverture > .bloc, .couverture > .deux-colonnes, .couverture > .trois-colonnes, .couverture > .mention { margin-left: 4mm; margin-right: 4mm; }
      .chiffres { grid-template-columns: repeat(2, 1fr); }
      .deux-colonnes, .trois-colonnes { grid-template-columns: 1fr; gap: 2mm; }
      .ensemble { overflow-x: auto; }
      table { min-width: 560px; }
      .corps.avec-visuel { grid-template-columns: 1fr; }
      .visuel { width: 100%; }
      .astuce { text-align: left; }
    }
    @media print {
      :host { background: #fff; }
      .no-print { display: none !important; }
      .rapport { box-shadow: none; margin: 0; max-width: none; }
      section { padding: 0 0 6mm; }
      section + section { border-top: none; }
      .couverture { break-after: page; padding: 0; }
      .bande { margin: 0; border-radius: 0 0 10px 10px; }
      .couverture > .chiffres, .couverture > .bloc, .couverture > .deux-colonnes, .couverture > .trois-colonnes, .couverture > .mention { margin-left: 4mm; margin-right: 4mm; }
      .details { break-before: page; }
      * { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
    }
  `,
})
export class ExportCalendrierComponent implements OnInit {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  /** Calendrier du CM (connecté) ou jeton d'un lien de partage public. */
  id = input<string>();
  jeton = input<string>();
  /** « client » : version à partager, sans statuts, prompts, retours ni zone d'avis. */
  mode = input<string>();
  partage = computed(() => !!this.jeton() || this.mode() === 'client');

  cal = signal<Calendrier | null>(null);
  client = signal<Client | null>(null);
  erreur = signal('');
  aujourdhui = new Date();
  opt = { details: true, prompts: false, visuels: true, retours: true, avis: true };

  reseaux: Partial<Record<string, string>> = LIBELLES_RESEAU;
  formats: Partial<Record<string, string>> = LIBELLES_FORMAT;
  statuts = LIBELLES_STATUT;
  objectifs = OBJECTIFS;
  decisions = DECISIONS;
  criteres = ['Pertinence', 'Qualité du texte', 'Adapté au réseau'];
  criteresGlobaux = ['Cohérence avec la marque', 'Variété des contenus', 'Équilibre des piliers', 'Faisabilité'];

  c1 = computed(() => this.couleur(0) ?? '#15803d');
  c2 = computed(() => this.couleur(1) ?? `color-mix(in srgb, ${this.c1()} 70%, black)`);
  logo = computed(() => {
    const a = this.client()?.assets ?? [];
    return (a.find(x => x.type === 'LOGO_COULEUR') ?? a.find(x => x.type === 'ICONE') ?? a.find(x => x.type === 'LOGO_NOIR'))?.url ?? null;
  });
  auteur = computed(() => this.auth.session()?.nom ?? '');
  nbVideos = computed(() => (this.cal()?.publications ?? []).filter(p => p.format === 'VIDEO').length);

  semaines = computed<Semaine[]>(() => {
    const c = this.cal();
    if (!c) return [];
    const debut = new Date(c.debut + 'T00:00:00');
    const groupes = new Map<number, Publication[]>();
    for (const p of c.publications) {
      const n = Math.floor((new Date(p.date + 'T00:00:00').getTime() - debut.getTime()) / (7 * 864e5));
      groupes.set(n, [...(groupes.get(n) ?? []), p]);
    }
    return [...groupes.entries()].sort((a, b) => a[0] - b[0]).map(([n, pubs]) => {
      const d = new Date(debut.getTime() + n * 7 * 864e5);
      const f = new Date(d.getTime() + 6 * 864e5);
      const fmt = (x: Date) => x.toLocaleDateString('fr-FR', { day: 'numeric', month: 'long' });
      return { titre: `Semaine ${n + 1} · du ${fmt(d)} au ${fmt(f)}`, publications: pubs };
    });
  });

  parReseau = computed(() => this.compter(p => this.reseaux[p.reseau] ?? p.reseau));
  parFormat = computed(() => this.compter(p => this.formats[p.format] ?? p.format));
  parPilier = computed(() => this.compter(p => p.pilier || 'Sans pilier'));

  ngOnInit() {
    const jeton = this.jeton();
    if (jeton) {
      this.api.partagePublic(jeton).subscribe({
        next: p => {
          this.client.set({
            nom: p.clientNom, langue: 'fr', tutoiement: false, offres: [], cibles: [], concurrents: [],
            couleurs: p.couleurs ?? [], reseaux: {},
            assets: p.logoUrl ? [{ id: 'logo', type: 'LOGO_COULEUR', url: p.logoUrl }] : [],
          });
          this.cal.set({
            id: '', clientId: '', clientNom: p.clientNom, debut: p.debut, fin: p.fin, reseaux: p.reseaux ?? [],
            rythme: {}, objectifs: p.objectifs, tempsForts: p.tempsForts, langue: 'fr', strategie: p.strategie,
            piliers: p.piliers, statutGeneration: 'TERMINE', total: p.publications.length, detaillees: p.publications.length,
            publications: p.publications.map(x => ({
              id: x.id, calendrierId: '', date: x.date, reseau: x.reseau, format: x.format, pilier: x.pilier,
              idee: x.idee, objectif: x.objectif, legende: x.legende, hashtags: x.hashtags, cta: x.cta,
              brief: x.brief, scenario: x.scenario, detailGenere: true, statut: 'VALIDEE' as const, version: 0,
              visuels: (x.medias ?? []).map((m, i) => ({ id: String(i), url: m.url, type: m.type, prompt: '', date: '' })),
            })),
          });
          document.title = `Calendrier ${p.clientNom} ${p.debut} au ${p.fin}`;
        },
        error: e => this.erreur.set(messageErreur(e)),
      });
      return;
    }
    if (this.mode() === 'client') {
      this.opt = { details: true, prompts: false, visuels: true, retours: false, avis: false };
    }
    const id = this.id();
    if (!id) return;
    this.api.calendrier(id).subscribe({
      next: c => {
        this.cal.set(c);
        document.title = `Calendrier ${c.clientNom} ${c.debut} au ${c.fin}`;
        this.api.client(c.clientId).subscribe({ next: cl => this.client.set(cl), error: () => {} });
      },
      error: e => this.erreur.set(messageErreur(e)),
    });
  }

  apercu(p: Publication): string | null {
    return p.visuels?.find(v => (v.type ?? 'IMAGE') === 'IMAGE')?.url ?? null;
  }

  video(p: Publication): string | null {
    return p.visuels?.find(v => v.type === 'VIDEO')?.url ?? null;
  }

  imprimer() {
    window.print();
  }

  max(liste: Compte[]) {
    return Math.max(1, ...liste.map(x => x.nombre));
  }

  private compter(cle: (p: Publication) => string): Compte[] {
    const m = new Map<string, number>();
    for (const p of this.cal()?.publications ?? []) m.set(cle(p), (m.get(cle(p)) ?? 0) + 1);
    return [...m.entries()].map(([libelle, nombre]) => ({ libelle, nombre })).sort((a, b) => b.nombre - a.nombre);
  }

  private couleur(i: number): string | null {
    const c = this.client()?.couleurs?.[i];
    return c && /^#[0-9a-fA-F]{6}$/.test(c) ? c : null;
  }
}
