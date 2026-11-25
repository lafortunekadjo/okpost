import { Component, inject, input, output, signal } from '@angular/core';
import { ApiService, messageErreur } from '../core/api.service';

type Mode = 'CORRIGER' | 'REFORMULER' | 'RACCOURCIR';

const LIBELLES: Record<Mode, string> = { CORRIGER: 'Corriger', REFORMULER: 'Reformuler', RACCOURCIR: 'Raccourcir' };

/**
 * Aide à la rédaction d'un champ texte de la fiche client : l'IA propose, l'utilisateur décide.
 * Le texte n'est jamais remplacé sans clic sur « Utiliser ce texte ».
 */
@Component({
  selector: 'app-assistant-redaction',
  template: `
    <div class="assistant">
      <span class="aide">Avec l'IA :</span>
      @for (m of modes; track m) {
        <button type="button" class="bouton secondaire petit" [disabled]="enCours() !== null" (click)="lancer(m)">
          {{ enCours() === m ? 'En cours…' : libelles[m] }}
        </button>
      }
    </div>
    @if (erreur()) { <div class="erreur" role="alert" style="margin-top:6px">{{ erreur() }}</div> }
    @if (proposition(); as p) {
      <div class="proposition" role="status">
        <strong class="aide">Proposition ({{ libelles[p.mode].toLowerCase() }}) :</strong>
        <p class="texte">{{ p.texte }}</p>
        <div class="ligne" style="gap:8px">
          <button type="button" class="bouton petit" (click)="utiliser(p.texte)">Utiliser ce texte</button>
          <button type="button" class="bouton secondaire petit" (click)="proposition.set(null)">Ignorer</button>
        </div>
      </div>
    }
  `,
  styles: `
    .assistant { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; margin-top: 6px; }
    .proposition { margin-top: 8px; padding: 10px 12px; border-radius: 8px; background: var(--vert-clair); }
    .texte { margin: 4px 0 10px; white-space: pre-line; }
  `,
})
export class AssistantRedactionComponent {
  private api = inject(ApiService);

  texte = input.required<string>();
  /** Nom du produit ou de l'entreprise : donne le contexte à l'IA. */
  sujet = input<string>('');
  champ = input<'produit' | 'activite'>('produit');
  /** Client dont la fiche (ton, langue) sert de contexte ; absent pour une fiche pas encore enregistrée. */
  clientId = input<string | undefined>(undefined);
  appliquer = output<string>();

  modes: Mode[] = ['CORRIGER', 'REFORMULER', 'RACCOURCIR'];
  libelles = LIBELLES;
  enCours = signal<Mode | null>(null);
  proposition = signal<{ texte: string; mode: Mode } | null>(null);
  erreur = signal('');

  lancer(mode: Mode) {
    const texte = this.texte().trim();
    this.erreur.set('');
    this.proposition.set(null);
    if (!texte) {
      this.erreur.set('Écris d\'abord quelques mots : l\'IA améliore un texte, elle n\'en invente pas.');
      return;
    }
    this.enCours.set(mode);
    this.api.reformuler({ texte, sujet: this.sujet() || undefined, mode, champ: this.champ(), clientId: this.clientId() }).subscribe({
      next: r => { this.proposition.set({ texte: r.texte, mode }); this.enCours.set(null); },
      error: e => { this.erreur.set(messageErreur(e)); this.enCours.set(null); },
    });
  }

  utiliser(texte: string) {
    this.appliquer.emit(texte);
    this.proposition.set(null);
  }
}
