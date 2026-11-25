import { Component, inject, input, output, signal } from '@angular/core';
import { ApiService, messageErreur } from '../core/api.service';

type Mode = 'CORRIGER' | 'REFORMULER' | 'RACCOURCIR' | 'PHOTO';

const LIBELLES: Record<Mode, string> = {
  CORRIGER: 'Corriger', REFORMULER: 'Reformuler', RACCOURCIR: 'Raccourcir', PHOTO: 'Décrire d\'après la photo',
};

/**
 * Aide à la rédaction d'un champ texte de la fiche client : l'IA propose, l'utilisateur ajuste puis décide.
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
      @if (champ() === 'produit') {
        <button type="button" class="bouton secondaire petit" [disabled]="enCours() !== null || !clientId() || !photoId()"
                [title]="!clientId() ? 'Enregistre d\\'abord la fiche' : !photoId() ? 'Rattache d\\'abord une photo à ce produit' : 'L\\'IA regarde la photo et propose une description brève'"
                (click)="lancer('PHOTO')">
          {{ enCours() === 'PHOTO' ? 'Lecture de la photo…' : libelles.PHOTO }}
        </button>
      }
    </div>
    @if (champ() === 'produit' && (!photoId() || !clientId())) {
      <p class="aide" style="margin:4px 0 0">Pour décrire le produit d'après son image, enregistre la fiche et rattache une photo au produit (section « Photos du produit »).</p>
    }
    @if (erreur()) { <div class="erreur" role="alert" style="margin-top:6px">{{ erreur() }}</div> }
    @if (proposition(); as p) {
      <div class="proposition" role="status">
        <strong class="aide">Proposition ({{ libelles[p.mode].toLowerCase() }}) — tu peux la modifier :</strong>
        <textarea rows="4" [value]="p.texte" (input)="modifier($any($event.target).value, $event)" aria-label="Texte proposé, modifiable"></textarea>
        <div class="ligne" style="gap:8px;flex-wrap:wrap">
          <button type="button" class="bouton petit" (click)="utiliser(p.texte)">Utiliser ce texte</button>
          @if (p.mode === 'PHOTO' && texte().trim()) {
            <button type="button" class="bouton secondaire petit" (click)="ajouter(p.texte)">Ajouter à la suite de la description</button>
          }
          <button type="button" class="bouton secondaire petit" (click)="proposition.set(null)">Ignorer</button>
        </div>
      </div>
    }
  `,
  styles: `
    .assistant { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; margin-top: 6px; }
    .proposition { margin-top: 8px; padding: 10px 12px; border-radius: 8px; background: var(--vert-clair); display: grid; gap: 8px; }
    .proposition textarea { width: 100%; }
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
  /** Photo du produit à décrire (banque d'images du client). */
  photoId = input<string | undefined>(undefined);
  appliquer = output<string>();

  modes: Mode[] = ['CORRIGER', 'REFORMULER', 'RACCOURCIR'];
  libelles = LIBELLES;
  enCours = signal<Mode | null>(null);
  proposition = signal<{ texte: string; mode: Mode } | null>(null);
  erreur = signal('');

  lancer(mode: Mode) {
    this.erreur.set('');
    this.proposition.set(null);
    const recu = {
      next: (r: { texte: string }) => { this.proposition.set({ texte: r.texte, mode }); this.enCours.set(null); },
      error: (e: unknown) => { this.erreur.set(messageErreur(e)); this.enCours.set(null); },
    };
    if (mode === 'PHOTO') {
      const id = this.clientId();
      const photo = this.photoId();
      if (!id || !photo) return;
      this.enCours.set(mode);
      this.api.decrireProduit(id, photo, this.sujet() || undefined).subscribe(recu);
      return;
    }
    const texte = this.texte().trim();
    if (!texte) {
      this.erreur.set('Écris d\'abord quelques mots : l\'IA améliore un texte, elle n\'en invente pas. Pour partir d\'une photo, utilise « Décrire d\'après la photo ».');
      return;
    }
    this.enCours.set(mode);
    this.api.reformuler({ texte, sujet: this.sujet() || undefined, mode, champ: this.champ(), clientId: this.clientId() }).subscribe(recu);
  }

  /** La proposition se retouche avant d'être utilisée ; l'événement ne remonte pas pour ne pas marquer la fiche comme modifiée. */
  modifier(texte: string, ev: Event) {
    ev.stopPropagation();
    this.proposition.update(p => (p ? { ...p, texte } : p));
  }

  utiliser(texte: string) {
    this.appliquer.emit(texte.trim());
    this.proposition.set(null);
  }

  ajouter(texte: string) {
    this.appliquer.emit(`${this.texte().trimEnd()} ${texte.trim()}`.trim());
    this.proposition.set(null);
  }
}
