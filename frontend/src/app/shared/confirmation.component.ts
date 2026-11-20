import { Component, ElementRef, effect, inject, viewChild } from '@angular/core';
import { ConfirmationService } from '../core/confirmation.service';

/** Boîte de confirmation unique, montée une fois à la racine de l'application. */
@Component({
  selector: 'app-confirmation',
  template: `
    @if (svc.demande(); as d) {
      <div class="voile" (click)="svc.repondre(false)">
        <div class="boite" role="alertdialog" aria-modal="true" aria-labelledby="conf-titre"
             [attr.aria-describedby]="d.message ? 'conf-message' : null"
             (click)="$event.stopPropagation()" (keydown)="touche($event)">
          <h2 id="conf-titre">{{ d.titre }}</h2>
          @if (d.message) { <p id="conf-message">{{ d.message }}</p> }
          <div class="boutons">
            <button #annuler type="button" class="bouton secondaire" (click)="svc.repondre(false)">Annuler</button>
            <button #valider type="button" class="bouton valider" [class.danger]="d.danger" (click)="svc.repondre(true)">{{ d.confirmer }}</button>
          </div>
        </div>
      </div>
    }
  `,
  styles: [`
    .voile { position: fixed; inset: 0; z-index: 1000; display: flex; align-items: center; justify-content: center; padding: 16px;
      background: rgba(17, 24, 39, .5); }
    .boite { width: 100%; max-width: 440px; background: #fff; border-radius: 14px; padding: 22px; box-shadow: 0 20px 50px rgba(17, 24, 39, .35); }
    h2 { margin: 0 0 8px; font-size: 1.1rem; }
    p { margin: 0 0 18px; color: var(--gris); }
    .boutons { display: flex; justify-content: flex-end; gap: 10px; flex-wrap: wrap; margin-top: 18px; }
    .valider.danger { background: var(--rouge); border-color: var(--rouge); color: #fff; }
    .valider.danger:hover { background: #991b1b; }
  `],
})
export class ConfirmationComponent {
  svc = inject(ConfirmationService);
  private annuler = viewChild<ElementRef<HTMLButtonElement>>('annuler');
  private valider = viewChild<ElementRef<HTMLButtonElement>>('valider');

  constructor() {
    // Le focus démarre sur « Annuler » : un Entrée distrait ne confirme pas une action destructive
    effect(() => {
      const bouton = this.annuler();
      if (bouton) setTimeout(() => bouton.nativeElement.focus());
    });
  }

  touche(ev: KeyboardEvent) {
    if (ev.key === 'Escape') {
      ev.preventDefault();
      this.svc.repondre(false);
      return;
    }
    // Le focus reste dans la boîte tant qu'elle est ouverte
    if (ev.key === 'Tab') {
      const a = this.annuler()?.nativeElement;
      const v = this.valider()?.nativeElement;
      if (!a || !v) return;
      if (ev.shiftKey && document.activeElement === a) { ev.preventDefault(); v.focus(); }
      else if (!ev.shiftKey && document.activeElement === v) { ev.preventDefault(); a.focus(); }
    }
  }
}
