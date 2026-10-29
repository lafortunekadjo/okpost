import { Component, model, input } from '@angular/core';

/** Saisie d'une liste de mots (cibles, concurrents, hashtags…) : Entrée ou virgule pour ajouter. */
@Component({
  selector: 'app-puces',
  template: `
    <div class="puces" style="margin-bottom:6px">
      @for (v of valeurs(); track $index) {
        <span class="puce">{{ v }} <button type="button" (click)="retirer($index)" [attr.aria-label]="'Retirer ' + v">×</button></span>
      }
    </div>
    <input type="text" [placeholder]="placeholder()" (keydown)="touche($event)" (blur)="ajouter($any($event.target))" />
  `,
})
export class PucesComponent {
  valeurs = model<string[]>([]);
  placeholder = input('Écris puis Entrée');

  touche(e: KeyboardEvent) {
    if (e.key === 'Enter' || e.key === ',') {
      e.preventDefault();
      this.ajouter(e.target as HTMLInputElement);
    }
  }

  ajouter(champ: HTMLInputElement) {
    const v = champ.value.trim().replace(/,$/, '');
    if (v && !this.valeurs().includes(v)) this.valeurs.set([...this.valeurs(), v]);
    champ.value = '';
  }

  retirer(i: number) {
    this.valeurs.set(this.valeurs().filter((_, k) => k !== i));
  }
}
