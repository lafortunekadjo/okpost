import { Component, DestroyRef, OnInit, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, messageErreur } from '../core/api.service';
import { ConfirmationService } from '../core/confirmation.service';
import { ContactValidation } from '../core/models';

/** Personnes autorisées à valider pour un client : chacune a son code, ce qui permet de savoir qui a validé quoi. */
@Component({
  selector: 'app-contacts-validation',
  imports: [DatePipe, FormsModule],
  template: `
    <section class="carte">
      <h2>Personnes autorisées à valider</h2>
      <p class="aide">
        Donne un code personnel à chaque personne qui valide les publications chez ce client (le gérant, l'assistante…).
        Dès qu'une personne est ajoutée, le lien de validation demande son code, et chaque validation est enregistrée à son nom.
        Sans personne ajoutée, le lien seul suffit, comme avant.
      </p>
      @if (erreur()) { <div class="erreur" role="alert">{{ erreur() }}</div> }

      @if (nouveau(); as n) {
        <div class="succes" role="status">
          Code de <strong>{{ n.nom }}</strong> : <strong class="code">{{ n.code }}</strong><br />
          <span class="aide">À transmettre à cette personne (WhatsApp, appel…). Il ne sera plus affiché : tu pourras en générer un nouveau si besoin.</span>
        </div>
      }

      @for (c of contacts(); track c.id) {
        <div class="contact">
          <div class="espace">
            <strong>{{ c.nom }}</strong>
            <span class="aide">
              @if (c.derniereUtilisation) { · dernière validation {{ c.derniereUtilisation | date: 'd MMM, HH:mm' }} } @else { · n'a pas encore validé }
            </span>
          </div>
          <button type="button" class="bouton secondaire petit" [disabled]="occupe()" (click)="renouveler(c)">Nouveau code</button>
          <button type="button" class="bouton danger petit" [disabled]="occupe()" (click)="retirer(c)">Retirer</button>
        </div>
      } @empty {
        @if (!chargement()) { <p class="aide">Aucune personne ajoutée : le lien de validation n'est protégé par aucun code.</p> }
      }

      <div class="ligne ajout">
        <input type="text" name="contact-nom" [(ngModel)]="nom" maxlength="150" placeholder="Nom de la personne (ex. Marie, gérante)"
               aria-label="Nom de la personne autorisée" (keydown.enter)="$event.preventDefault(); ajouter()" />
        <button type="button" class="bouton" [disabled]="occupe() || !nom.trim()" (click)="ajouter()">Ajouter</button>
      </div>
    </section>
  `,
  styles: `
    .contact { display: flex; gap: 8px; align-items: center; padding: 8px 0; border-bottom: 1px solid var(--gris-clair); flex-wrap: wrap; }
    .ajout { margin-top: 12px; gap: 8px; }
    .ajout input { flex: 1; }
    .code { font-size: 1.5rem; letter-spacing: .25em; font-variant-numeric: tabular-nums; }
  `,
})
export class ContactsValidationComponent implements OnInit {
  private api = inject(ApiService);
  private confirmation = inject(ConfirmationService);
  private destroy = inject(DestroyRef);

  clientId = input.required<string>();

  contacts = signal<ContactValidation[]>([]);
  nouveau = signal<{ nom: string; code: string } | null>(null);
  erreur = signal('');
  chargement = signal(true);
  occupe = signal(false);
  nom = '';

  ngOnInit() {
    this.api.contactsValidation(this.clientId()).subscribe({
      next: l => { this.contacts.set(l); this.chargement.set(false); },
      error: e => { this.erreur.set(messageErreur(e)); this.chargement.set(false); },
    });
    // Le code affiché disparaît quand on quitte l'écran : il n'est pas conservé côté navigateur
    this.destroy.onDestroy(() => this.nouveau.set(null));
  }

  ajouter() {
    const nom = this.nom.trim();
    if (!nom) return;
    this.occupe.set(true);
    this.erreur.set('');
    this.api.ajouterContactValidation(this.clientId(), nom).subscribe({
      next: r => {
        this.contacts.update(l => [...l, r.contact].sort((a, b) => a.nom.localeCompare(b.nom)));
        this.nouveau.set({ nom: r.contact.nom, code: r.code });
        this.nom = '';
        this.occupe.set(false);
      },
      error: e => { this.erreur.set(messageErreur(e)); this.occupe.set(false); },
    });
  }

  async renouveler(c: ContactValidation) {
    if (!(await this.confirmation.demander({
      titre: `Nouveau code pour ${c.nom} ?`,
      message: 'Son ancien code ne fonctionnera plus. Pense à lui transmettre le nouveau.',
      confirmer: 'Générer un nouveau code',
    }))) return;
    this.occupe.set(true);
    this.api.renouvelerContactValidation(this.clientId(), c.id).subscribe({
      next: r => {
        this.contacts.update(l => l.map(x => (x.id === c.id ? r.contact : x)));
        this.nouveau.set({ nom: r.contact.nom, code: r.code });
        this.occupe.set(false);
      },
      error: e => { this.erreur.set(messageErreur(e)); this.occupe.set(false); },
    });
  }

  async retirer(c: ContactValidation) {
    if (!(await this.confirmation.demander({
      titre: `Retirer ${c.nom} ?`,
      message: 'Son code ne fonctionnera plus. Ses validations passées restent enregistrées à son nom.',
      confirmer: 'Retirer',
      danger: true,
    }))) return;
    this.occupe.set(true);
    this.api.supprimerContactValidation(this.clientId(), c.id).subscribe({
      next: () => { this.contacts.update(l => l.filter(x => x.id !== c.id)); this.nouveau.set(null); this.occupe.set(false); },
      error: e => { this.erreur.set(messageErreur(e)); this.occupe.set(false); },
    });
  }
}
