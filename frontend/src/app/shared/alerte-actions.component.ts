import { Component, inject, input, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService, messageErreur } from '../core/api.service';
import { Alerte } from '../core/models';

/** Types d'alerte que le community manager peut régler d'un clic depuis le tableau de bord. */
const TYPES_ACTIONNABLES = ['ECHEC', 'EN_RETARD', 'SANS_HEURE', 'NON_PROGRAMMEE'];

/** Actions rapides sur une publication qui n'est pas partie : réessayer, programmer, reprogrammer à une autre heure. */
@Component({
  selector: 'app-alerte-actions',
  imports: [RouterLink],
  template: `
    @if (actionnable()) {
      <div class="actions">
        @if (compteAReconnecter()) {
          <span class="avertissement">
            Le compte {{ alerte().reseau === 'INSTAGRAM' ? 'Instagram' : 'Facebook' }} est à reconnecter : reconnecte-le d'abord, sinon l'essai échouera de nouveau.
            <a [routerLink]="['/clients', alerte().clientId]" fragment="reseaux">Reconnecter</a>
          </span>
        }
        <div class="boutons">
          @if (alerte().type === 'ECHEC' || alerte().type === 'EN_RETARD') {
            <button type="button" class="bouton petit" [disabled]="occupe()" (click)="reessayer()">{{ occupe() === 'reessai' ? 'Publication en cours…' : 'Réessayer maintenant' }}</button>
          } @else {
            <button type="button" class="bouton petit" [disabled]="occupe()" (click)="programmer()">{{ occupe() === 'prog' ? 'Programmation…' : 'Programmer à l\\'heure prévue' }}</button>
          }
          <button type="button" class="bouton secondaire petit" [disabled]="occupe()" (click)="ouvrirReprog()" [attr.aria-expanded]="reprog()">Reprogrammer…</button>
        </div>
        @if (reprog()) {
          <div class="reprog">
            <label for="quand-{{ alerte().publicationId }}" class="aide">Nouvelle date et heure</label>
            <input id="quand-{{ alerte().publicationId }}" type="datetime-local" [value]="quand()" [min]="minimum()" (input)="quand.set($any($event.target).value)" />
            <button type="button" class="bouton petit" [disabled]="occupe() || !quand()" (click)="reprogrammer()">{{ occupe() === 'reprog' ? 'Programmation…' : 'Confirmer' }}</button>
          </div>
        }
        @if (message(); as m) { <div [class]="m.ok ? 'succes' : 'erreur'" role="status">{{ m.texte }}</div> }
      </div>
    }
  `,
  styles: `
    .actions { margin-top: 8px; display: grid; gap: 8px; }
    .boutons, .reprog { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
    .avertissement { color: #b45309; font-size: .85rem; }
  `,
})
export class AlerteActionsComponent {
  private api = inject(ApiService);

  alerte = input.required<Alerte>();
  /** Un compte du même client et du même réseau est signalé « à reconnecter » : un nouvel essai échouerait. */
  compteAReconnecter = input(false);
  /** Émis quand la publication a changé d'état : le tableau de bord recharge ses alertes. */
  termine = output<void>();

  occupe = signal<'reessai' | 'prog' | 'reprog' | null>(null);
  reprog = signal(false);
  quand = signal('');
  message = signal<{ texte: string; ok: boolean } | null>(null);

  actionnable() {
    const a = this.alerte();
    return !!a.publicationId && TYPES_ACTIONNABLES.includes(a.type);
  }

  /** Heure locale du navigateur (celle du community manager) au format attendu par le champ datetime-local. */
  private local(d: Date) {
    const p = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
  }

  minimum() {
    return this.local(new Date(Date.now() + 3 * 60_000));
  }

  ouvrirReprog() {
    if (!this.reprog()) this.quand.set(this.local(new Date(Date.now() + 15 * 60_000)));
    this.reprog.set(!this.reprog());
  }

  reessayer() {
    const id = this.alerte().publicationId!;
    this.occupe.set('reessai');
    this.message.set(null);
    this.api.publierMaintenant(id).subscribe({
      next: p => {
        this.occupe.set(null);
        if (p.statut === 'PUBLIEE') {
          this.message.set({ texte: 'Publiée.', ok: true });
          this.termine.emit();
        } else {
          this.message.set({ texte: `Nouvel échec : ${p.erreurPublication ?? 'raison inconnue'}`, ok: false });
          this.termine.emit();
        }
      },
      error: e => { this.occupe.set(null); this.message.set({ texte: messageErreur(e), ok: false }); },
    });
  }

  programmer() {
    this.lancerProgrammation(undefined, 'prog');
  }

  reprogrammer() {
    const valeur = this.quand();
    const date = new Date(valeur);
    if (!valeur || isNaN(date.getTime())) {
      this.message.set({ texte: 'Choisis une date et une heure valides.', ok: false });
      return;
    }
    this.lancerProgrammation(date.toISOString(), 'reprog');
  }

  private lancerProgrammation(quand: string | undefined, action: 'prog' | 'reprog') {
    this.occupe.set(action);
    this.message.set(null);
    this.api.programmer(this.alerte().publicationId!, quand).subscribe({
      next: () => { this.occupe.set(null); this.reprog.set(false); this.message.set({ texte: 'Programmée.', ok: true }); this.termine.emit(); },
      error: e => { this.occupe.set(null); this.message.set({ texte: messageErreur(e), ok: false }); },
    });
  }
}
