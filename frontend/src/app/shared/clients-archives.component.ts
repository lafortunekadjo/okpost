import { Component, output, signal } from '@angular/core';
import { inject } from '@angular/core';
import { ApiService, messageErreur } from '../core/api.service';
import { ConfirmationService } from '../core/confirmation.service';
import { ClientResume } from '../core/models';

/** Clients archivés : on peut les restaurer, ou les supprimer définitivement (administrateur). */
@Component({
  selector: 'app-clients-archives',
  template: `
    <div class="carte archives">
      <button type="button" class="titre" (click)="basculer()" [attr.aria-expanded]="ouvert()">
        {{ ouvert() ? '▾' : '▸' }} Clients archivés
      </button>
      @if (ouvert()) {
        @if (erreur()) { <div class="erreur" role="alert">{{ erreur() }}</div> }
        @if (clients() === null) {
          <p class="aide">Chargement…</p>
        } @else if (!clients()!.length) {
          <p class="aide">Aucun client archivé.</p>
        } @else {
          <p class="aide">Un client archivé n'apparaît plus dans la liste et ses programmations sont annulées. Ses données sont conservées jusqu'à une suppression définitive.</p>
          @for (c of clients(); track c.id) {
            <div class="ligne-archive">
              <strong class="espace">{{ c.nom }}</strong>
              <button type="button" class="bouton secondaire petit" [disabled]="occupe()" (click)="restaurer(c)">Restaurer</button>
              <button type="button" class="bouton danger petit" [disabled]="occupe()" (click)="supprimer(c)">Supprimer définitivement</button>
            </div>
          }
        }
      }
    </div>
  `,
  styles: `
    .archives { margin-top: 24px; }
    .titre { background: none; border: 0; padding: 0; font: inherit; font-weight: 600; cursor: pointer; color: var(--gris); }
    .ligne-archive { display: flex; gap: 8px; align-items: center; padding: 8px 0; border-top: 1px solid var(--gris-clair); flex-wrap: wrap; }
  `,
})
export class ClientsArchivesComponent {
  private api = inject(ApiService);
  private confirmation = inject(ConfirmationService);

  /** Émis quand un client est restauré : la liste principale doit se recharger. */
  restaure = output<void>();

  ouvert = signal(false);
  clients = signal<ClientResume[] | null>(null);
  erreur = signal('');
  occupe = signal(false);

  basculer() {
    this.ouvert.update(v => !v);
    if (this.ouvert() && this.clients() === null) this.charger();
  }

  private charger() {
    this.api.clientsArchives().subscribe({
      next: l => this.clients.set(l),
      error: e => { this.erreur.set(messageErreur(e)); this.clients.set([]); },
    });
  }

  restaurer(c: ClientResume) {
    this.occupe.set(true);
    this.erreur.set('');
    this.api.restaurerClient(c.id).subscribe({
      next: () => { this.occupe.set(false); this.charger(); this.restaure.emit(); },
      error: e => { this.occupe.set(false); this.erreur.set(messageErreur(e)); },
    });
  }

  async supprimer(c: ClientResume) {
    if (!(await this.confirmation.demander({
      titre: `Supprimer définitivement ${c.nom} ?`,
      message: 'Sa fiche, ses calendriers, ses publications, ses photos et ses visuels seront effacés pour toujours. Les posts déjà publiés sur Facebook ou Instagram restent en ligne. Cette action est irréversible (réservée aux administrateurs).',
      confirmer: 'Supprimer définitivement',
      danger: true,
    }))) return;
    this.occupe.set(true);
    this.erreur.set('');
    this.api.supprimerClientDefinitivement(c.id).subscribe({
      next: () => { this.occupe.set(false); this.charger(); },
      error: e => { this.occupe.set(false); this.erreur.set(messageErreur(e)); },
    });
  }
}
