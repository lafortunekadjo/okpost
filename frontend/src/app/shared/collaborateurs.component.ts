import { Component, OnInit, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, messageErreur } from '../core/api.service';
import { ConfirmationService } from '../core/confirmation.service';
import { Collaborateur } from '../core/models';

/** Personnes d'un autre espace invitées à travailler sur CE client (le propriétaire de l'espace les invite et peut les retirer). */
@Component({
  selector: 'app-collaborateurs',
  imports: [DatePipe, FormsModule],
  template: `
    <section class="carte" id="collaborateurs">
      <h2>Collaborateurs sur ce client</h2>
      <p class="aide">
        Invite une personne qui a déjà un compte OkPost : elle verra ce client dans « Clients partagés avec moi » et pourra y travailler
        (fiche, calendriers, publications, visuels, réseaux). Elle ne voit ni tes autres clients, ni tes membres, ni ton abonnement.
        Ses calendriers et images IA comptent sur ton abonnement. Tu peux retirer l'accès à tout moment.
      </p>
      @if (erreur()) { <div class="erreur" role="alert">{{ erreur() }}</div> }
      @if (message()) { <div class="succes" role="status">{{ message() }}</div> }

      @for (c of collaborateurs(); track c.id) {
        <div class="ligne-collab">
          <div class="espace"><strong>{{ c.nom }}</strong> <span class="aide">{{ c.email }} · depuis le {{ c.depuis | date: 'd MMM y' }}</span></div>
          <button type="button" class="bouton danger petit" [disabled]="occupe()" (click)="retirer(c)">Retirer l'accès</button>
        </div>
      } @empty {
        @if (!chargement()) { <p class="aide">Personne n'a été invité sur ce client.</p> }
      }

      <div class="ligne ajout">
        <input type="email" name="collab-email" [(ngModel)]="email" maxlength="190" placeholder="E-mail du compte OkPost à inviter"
               aria-label="E-mail de la personne à inviter" (keydown.enter)="$event.preventDefault(); inviter()" />
        <button type="button" class="bouton" [disabled]="occupe() || !email.trim()" (click)="inviter()">{{ occupe() ? 'Invitation…' : 'Inviter' }}</button>
      </div>
    </section>
  `,
  styles: `
    .ligne-collab { display: flex; gap: 8px; align-items: center; padding: 8px 0; border-bottom: 1px solid var(--gris-clair); flex-wrap: wrap; }
    .ajout { margin-top: 12px; gap: 8px; }
    .ajout input { flex: 1; }
  `,
})
export class CollaborateursComponent implements OnInit {
  private api = inject(ApiService);
  private confirmation = inject(ConfirmationService);

  clientId = input.required<string>();

  collaborateurs = signal<Collaborateur[]>([]);
  erreur = signal('');
  message = signal('');
  chargement = signal(true);
  occupe = signal(false);
  email = '';

  ngOnInit() {
    this.api.collaborateurs(this.clientId()).subscribe({
      next: l => { this.collaborateurs.set(l); this.chargement.set(false); },
      error: e => { this.erreur.set(messageErreur(e)); this.chargement.set(false); },
    });
  }

  inviter() {
    const email = this.email.trim();
    if (!email) return;
    this.occupe.set(true);
    this.erreur.set('');
    this.message.set('');
    this.api.inviterCollaborateur(this.clientId(), email).subscribe({
      next: c => {
        this.collaborateurs.update(l => [...l, c]);
        this.message.set(`${c.nom} a maintenant accès à ce client (un e-mail l'a prévenu(e) si l'envoi est configuré).`);
        this.email = '';
        this.occupe.set(false);
      },
      error: e => { this.erreur.set(messageErreur(e)); this.occupe.set(false); },
    });
  }

  async retirer(c: Collaborateur) {
    if (!(await this.confirmation.demander({
      titre: `Retirer l'accès de ${c.nom} ?`,
      message: 'Cette personne ne pourra plus ouvrir ce client. Ce qu\'elle a déjà fait (calendriers, publications) reste dans la fiche.',
      confirmer: 'Retirer l\'accès',
      danger: true,
    }))) return;
    this.occupe.set(true);
    this.erreur.set('');
    this.message.set('');
    this.api.retirerCollaborateur(this.clientId(), c.id).subscribe({
      next: () => { this.collaborateurs.update(l => l.filter(x => x.id !== c.id)); this.occupe.set(false); },
      error: e => { this.erreur.set(messageErreur(e)); this.occupe.set(false); },
    });
  }
}
