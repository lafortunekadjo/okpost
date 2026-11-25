import { Component, inject, input, output, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiService, messageErreur } from '../core/api.service';
import { Offre } from '../core/models';

type Mode = 'CORRIGER' | 'REFORMULER' | 'RACCOURCIR';

interface Ligne { offre: Offre; avant: string; apres: string; coche: boolean; erreur?: string; }

const MAX_PAR_LOT = 30;

/**
 * Améliore d'un coup les descriptions de toutes les offres : l'IA traite chaque description, puis on relit,
 * on ajuste et on choisit ce qu'on garde. Rien n'est modifié avant « Appliquer ».
 */
@Component({
  selector: 'app-reformuler-tout',
  template: `
    <div class="lot">
      <button type="button" class="bouton secondaire" (click)="ouvert.set(!ouvert())" [attr.aria-expanded]="ouvert()">
        {{ ouvert() ? 'Fermer' : '✨ Améliorer toutes les descriptions avec l\\'IA' }}
      </button>
      @if (ouvert()) {
        <div class="panneau">
          <p class="aide">L'IA traite chaque description (jusqu'à {{ max }} par lot). Tu relis, tu ajustes si besoin, puis tu choisis ce que tu gardes : rien n'est modifié avant « Appliquer ».</p>
          <div class="ligne" style="gap:8px;flex-wrap:wrap">
            <button type="button" class="bouton petit" [disabled]="enCours()" (click)="lancer('REFORMULER')">Tout reformuler</button>
            <button type="button" class="bouton secondaire petit" [disabled]="enCours()" (click)="lancer('CORRIGER')">Tout corriger</button>
            <button type="button" class="bouton secondaire petit" [disabled]="enCours()" (click)="lancer('RACCOURCIR')">Tout raccourcir</button>
            @if (enCours()) { <span class="aide">Traitement… {{ progression() }}</span> }
          </div>
          @if (erreur()) { <div class="erreur" role="alert" style="margin-top:8px">{{ erreur() }}</div> }

          @if (lignes().length) {
            <div class="resultats">
              @for (l of lignes(); track $index) {
                <div class="resultat" [class.echec]="l.erreur">
                  <label class="case">
                    <input type="checkbox" [checked]="l.coche" [disabled]="!!l.erreur" (change)="cocher(l, $any($event.target).checked)" />
                    <strong>{{ l.offre.nom }}</strong>
                  </label>
                  @if (l.erreur) {
                    <p class="aide">{{ l.erreur }}</p>
                  } @else {
                    <details><summary class="aide">Voir l'ancien texte</summary><p class="avant">{{ l.avant }}</p></details>
                    <textarea rows="3" [value]="l.apres" (input)="modifier(l, $any($event.target).value, $event)" [attr.aria-label]="'Nouveau texte de ' + l.offre.nom + ', modifiable'"></textarea>
                  }
                </div>
              }
            </div>
            <div class="ligne" style="gap:8px;margin-top:10px">
              <button type="button" class="bouton" [disabled]="enCours() || nbCoches() === 0" (click)="appliquer()">Appliquer ({{ nbCoches() }})</button>
              <button type="button" class="bouton secondaire" [disabled]="enCours()" (click)="annuler()">Annuler</button>
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: `
    .lot { margin: 10px 0 14px; }
    .panneau { margin-top: 10px; padding: 12px 14px; border: 1px solid var(--gris-clair); border-radius: 10px; }
    .resultats { display: grid; gap: 10px; margin-top: 12px; }
    .resultat { padding: 10px 12px; border-radius: 8px; background: var(--vert-clair); display: grid; gap: 6px; }
    .resultat.echec { background: #fef2f2; }
    .resultat textarea { width: 100%; }
    .avant { margin: 4px 0 0; white-space: pre-line; color: var(--gris); font-size: .9rem; }
    .case { display: flex; gap: 8px; align-items: center; }
  `,
})
export class ReformulerToutComponent {
  private api = inject(ApiService);

  /** Les offres de la fiche (leurs descriptions sont modifiées au moment d'« Appliquer »). */
  offres = input.required<Offre[]>();
  clientId = input<string | undefined>(undefined);
  /** Émis après « Appliquer » : la fiche a des modifications à enregistrer. */
  modifie = output<void>();

  max = MAX_PAR_LOT;
  ouvert = signal(false);
  enCours = signal(false);
  progression = signal('');
  erreur = signal('');
  lignes = signal<Ligne[]>([]);

  nbCoches() {
    return this.lignes().filter(l => l.coche && !l.erreur).length;
  }

  async lancer(mode: Mode) {
    const cibles = this.offres().filter(o => o.nom?.trim() && o.description?.trim());
    this.erreur.set('');
    this.lignes.set([]);
    if (!cibles.length) {
      this.erreur.set('Aucune offre n\'a de description à améliorer. Écris-en une, ou utilise « Décrire d\'après la photo » sur chaque produit.');
      return;
    }
    const lot = cibles.slice(0, MAX_PAR_LOT);
    if (cibles.length > MAX_PAR_LOT) {
      this.erreur.set(`${cibles.length} descriptions : seules les ${MAX_PAR_LOT} premières sont traitées. Relance ensuite pour la suite.`);
    }
    this.enCours.set(true);
    const resultats: Ligne[] = [];
    for (let i = 0; i < lot.length; i++) {
      const o = lot[i];
      this.progression.set(`${i + 1} / ${lot.length}`);
      try {
        const r = await firstValueFrom(this.api.reformuler({
          texte: o.description!, sujet: o.nom, mode, champ: 'produit', clientId: this.clientId(),
        }));
        resultats.push({ offre: o, avant: o.description!, apres: r.texte, coche: r.texte.trim() !== o.description!.trim() });
      } catch (e) {
        const message = messageErreur(e);
        resultats.push({ offre: o, avant: o.description!, apres: '', coche: false, erreur: message });
        // Limite d'appels atteinte : inutile d'insister sur les offres suivantes
        if (message.includes('Trop de reformulations')) break;
      }
      this.lignes.set([...resultats]);
    }
    this.enCours.set(false);
    this.progression.set('');
  }

  cocher(l: Ligne, coche: boolean) {
    l.coche = coche;
    this.lignes.set([...this.lignes()]);
  }

  modifier(l: Ligne, texte: string, ev: Event) {
    ev.stopPropagation();
    l.apres = texte;
  }

  appliquer() {
    let n = 0;
    for (const l of this.lignes()) {
      if (l.coche && !l.erreur && l.apres.trim()) {
        l.offre.description = l.apres.trim();
        n++;
      }
    }
    if (n) this.modifie.emit();
    this.annuler();
  }

  annuler() {
    this.lignes.set([]);
    this.erreur.set('');
    this.ouvert.set(false);
  }
}
