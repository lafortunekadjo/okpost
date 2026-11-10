import { Component, OnInit, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService, messageErreur } from '../core/api.service';
import { LIBELLES_RESEAU, PropositionEvenement, RESEAUX } from '../core/models';

/** Temps fort affiché dans la liste : du catalogue OkPost ou suggéré par l'IA. */
interface TempsFort { cle: string; nom: string; debut?: string | null; fin?: string; categorie: string; raison: string;
  angle?: string; approximative: boolean; ia: boolean; coche: boolean; }

const LIBELLES_CATEGORIE: Record<string, string> = {
  NATIONAL: 'Fête nationale', RELIGIEUX: 'Religieux', COMMERCIAL: 'Commercial', MONDIAL: 'Journée mondiale', SAISON: 'Saison', IA: 'Suggestion IA',
};

function iso(d: Date) {
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}

@Component({
  selector: 'app-nouveau-calendrier',
  imports: [FormsModule, RouterLink, DatePipe],
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
          <div class="champ"><label>Du</label><input type="date" name="debut" [(ngModel)]="debut" (ngModelChange)="chargerEvenements()" required /></div>
          <div class="champ"><label>Au <span class="aide">6 semaines maximum</span></label><input type="date" name="fin" [(ngModel)]="fin" (ngModelChange)="chargerEvenements()" required /></div>
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
        <div class="champ analyse">
          @if (pageReliee()) {
            <label class="case"><input type="checkbox" name="analyser" [(ngModel)]="analyserPage" />
              Analyser les publications de la Page Facebook (90 derniers jours)</label>
            <p class="aide">L’IA tient compte de ce qui a marché, évite de répéter les sujets récents et reprend le ton réel de la Page.
              <a href="" (click)="$event.preventDefault(); voirAnalyse()">Voir ce qu’elle va apprendre</a></p>
            @if (apercu()) { <pre class="apercu">{{ apercu() }}</pre> }
            @if (erreurApercu()) { <p class="aide rouge">{{ erreurApercu() }}</p> }
          } @else if (pageReliee() === false) {
            <p class="aide">Relie la Page Facebook du client (page du client → Réseaux connectés) pour que l’IA analyse ses publications précédentes.</p>
          }
        </div>
        <div class="champ"><label>Objectifs de la période</label>
          <textarea name="objectifs" rows="3" [(ngModel)]="objectifs" placeholder="Ex. faire connaître la nouvelle formation, remplir la session de décembre"></textarea></div>
        <div class="champ">
          <label>Fêtes et journées proposées <span class="aide">selon le secteur et le pays du client ; décoche ce qui ne convient pas</span></label>
          @if (chargementEvenements()) { <p class="aide">Recherche des temps forts de la période…</p> }
          @else if (!tempsFortsListe().length) { <p class="aide">Aucune fête ni journée du catalogue sur cette période pour ce client.</p> }
          <div class="evenements">
            @for (t of tempsFortsListe(); track t.cle) {
              <label class="evenement" [class.decoche]="!t.coche">
                <input type="checkbox" [name]="'ev-' + t.cle" [(ngModel)]="t.coche" />
                <span class="quand">
                  @if (t.debut) { {{ t.debut | date: 'd MMM' }}@if (t.fin) { – {{ t.fin | date: 'd MMM' }} } }
                  @else { date à préciser }
                </span>
                <span class="quoi">
                  <strong>{{ t.nom }}</strong>
                  <span class="pastille cat-{{ t.ia ? 'IA' : t.categorie }}">{{ libelleCategorie(t) }}</span>
                  <span class="aide bloc">{{ t.angle || t.raison }}@if (t.approximative) { · <em>date à un jour près</em> }@if (t.ia) { · <em>à vérifier</em> }</span>
                </span>
              </label>
            }
          </div>
          <button type="button" class="bouton secondaire petit" (click)="suggerer()" [disabled]="suggestionEnCours()">
            @if (suggestionEnCours()) { L’IA réfléchit… } @else { Suggérer d’autres temps forts liés au métier (IA) }</button>
          @if (erreurSuggestion()) { <p class="aide rouge">{{ erreurSuggestion() }}</p> }
        </div>
        <div class="champ"><label>Autres temps forts à couvrir <span class="aide">promotions, lancements, événements du client</span></label>
          <textarea name="temps" rows="2" [(ngModel)]="tempsForts" placeholder="Ex. promotion -20 % du 10 au 15, ouverture de la 2e agence le 22"></textarea></div>
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
    .evenements { display: flex; flex-direction: column; margin-bottom: 10px; }
    .evenement { display: flex; gap: 10px; align-items: flex-start; padding: 8px 0; border-bottom: 1px solid var(--gris-clair);
      font-weight: 400; margin: 0; cursor: pointer; }
    .evenement input { margin-top: 3px; width: auto; }
    .evenement.decoche { opacity: .5; }
    .quand { min-width: 92px; font-size: .85rem; color: var(--gris); padding-top: 1px; }
    .quoi { flex: 1; }
    .quoi .pastille { margin-left: 6px; font-size: .7rem; vertical-align: 1px; }
    .bloc { display: block; margin-top: 2px; }
    .cat-IA { background: #fff4d6; color: #8a5a00; }
    .rouge { color: var(--rouge, #c0392b); }
    .analyse { background: #f6faf7; border-radius: 10px; padding: 12px 14px; }
    .analyse .case { flex: none; }
    .apercu { white-space: pre-wrap; font-family: inherit; font-size: .85rem; background: #fff; border: 1px solid var(--gris-clair);
      border-radius: 8px; padding: 10px; max-height: 280px; overflow: auto; }
  `,
})
export class NouveauCalendrierComponent implements OnInit {
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
  tempsFortsListe = signal<TempsFort[]>([]);
  chargementEvenements = signal(false);
  suggestionEnCours = signal(false);
  erreurSuggestion = signal('');
  private minuteur?: ReturnType<typeof setTimeout>;

  pageReliee = signal<boolean | null>(null);
  analyserPage = true;
  apercu = signal('');
  erreurApercu = signal('');

  ngOnInit() {
    this.chargerEvenements();
    this.api.etatPage(this.clientId()).subscribe({ next: r => this.pageReliee.set(r.pageReliee), error: () => this.pageReliee.set(false) });
  }

  voirAnalyse() {
    this.apercu.set('');
    this.erreurApercu.set('Lecture de la Page…');
    this.api.apercuAnalysePage(this.clientId()).subscribe({
      next: r => { this.apercu.set(r.texte); this.erreurApercu.set(''); },
      error: e => this.erreurApercu.set(messageErreur(e)),
    });
  }

  /** Recharge les propositions du catalogue quand la période change ; garde les suggestions IA et les choix faits. */
  chargerEvenements() {
    clearTimeout(this.minuteur);
    this.minuteur = setTimeout(() => {
      if (!this.debut || !this.fin || this.fin < this.debut) return;
      this.chargementEvenements.set(true);
      this.api.evenementsProposes(this.clientId(), this.debut, this.fin).subscribe({
        next: liste => {
          const avant = new Map(this.tempsFortsListe().map(t => [t.cle, t.coche]));
          const catalogue = liste.map(p => this.versTempsFort(p, avant));
          const ia = this.tempsFortsListe().filter(t => t.ia && (!t.debut || (t.debut >= this.debut && t.debut <= this.fin)));
          this.tempsFortsListe.set(this.trier([...catalogue, ...ia]));
          this.chargementEvenements.set(false);
        },
        error: () => this.chargementEvenements.set(false),
      });
    }, 300);
  }

  suggerer() {
    this.suggestionEnCours.set(true);
    this.erreurSuggestion.set('');
    const deja = this.tempsFortsListe().map(t => t.nom);
    this.api.suggestionsTempsForts(this.clientId(), this.debut, this.fin, deja).subscribe({
      next: liste => {
        const connus = new Set(deja.map(n => n.toLowerCase()));
        const nouveaux: TempsFort[] = liste.filter(x => !connus.has(x.nom.toLowerCase())).map((x, i) => ({
          cle: 'ia-' + Date.now() + '-' + i, nom: x.nom, debut: x.date ?? null, categorie: 'IA', raison: '',
          angle: x.angle, approximative: false, ia: true, coche: false,
        }));
        this.tempsFortsListe.update(l => this.trier([...l, ...nouveaux]));
        if (!nouveaux.length) this.erreurSuggestion.set("L'IA n'a rien trouvé de plus pour cette période.");
        this.suggestionEnCours.set(false);
      },
      error: e => { this.erreurSuggestion.set(messageErreur(e)); this.suggestionEnCours.set(false); },
    });
  }

  libelleCategorie(t: TempsFort) {
    return LIBELLES_CATEGORIE[t.ia ? 'IA' : t.categorie] ?? t.categorie;
  }

  private versTempsFort(p: PropositionEvenement, avant: Map<string, boolean>): TempsFort {
    const cle = p.id + '-' + p.debut;
    return { cle, nom: p.nom, debut: p.debut, fin: p.fin, categorie: p.categorie, raison: p.raison, angle: p.angle,
      approximative: p.approximative, ia: false, coche: avant.get(cle) ?? true };
  }

  private trier(l: TempsFort[]) {
    return [...l].sort((a, b) => (a.debut ?? '9999').localeCompare(b.debut ?? '9999'));
  }

  /** Texte envoyé à l'IA : temps forts cochés, avec leurs dates, puis ceux saisis à la main. */
  private texteTempsForts() {
    const lignes = this.tempsFortsListe().filter(t => t.coche).map(t => {
      const quand = t.debut ? (t.fin ? `du ${t.debut} au ${t.fin}` : `le ${t.debut}`) : 'date à préciser, ne pas l\'écrire';
      const notes = [t.angle, t.approximative ? 'date approximative' : '', t.ia ? 'suggestion à traiter avec prudence' : '']
        .filter(Boolean).join(' ; ');
      return `- ${t.nom} (${quand})${notes ? ' : ' + notes : ''}`;
    });
    if (this.tempsForts.trim()) lignes.push('- ' + this.tempsForts.trim());
    return lignes.join('\n');
  }

  generer() {
    const r: Record<string, number> = {};
    for (const x of RESEAUX) if (this.actifs[x]) r[x] = this.rythme[x];
    if (!Object.keys(r).length) { this.erreur.set('Choisis au moins un réseau.'); return; }
    this.envoi.set(true);
    this.erreur.set('');
    this.api.creerCalendrier(this.clientId(), {
      debut: this.debut, fin: this.fin, rythme: r, objectifs: this.objectifs, tempsForts: this.texteTempsForts(),
      langue: this.langue || null, analyserPage: !!this.pageReliee() && this.analyserPage,
    }).subscribe({
      next: c => this.router.navigate(['/calendriers', c.id]),
      error: e => { this.erreur.set(messageErreur(e)); this.envoi.set(false); },
    });
  }
}
