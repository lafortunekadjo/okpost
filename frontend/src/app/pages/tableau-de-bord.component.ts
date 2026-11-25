import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService, messageErreur } from '../core/api.service';
import { Abonnement, Alerte, ClientResume, LIBELLES_RESEAU } from '../core/models';
import { DatePipe } from '@angular/common';
import { ClientsArchivesComponent } from '../shared/clients-archives.component';
import { AlerteActionsComponent } from '../shared/alerte-actions.component';

@Component({
  selector: 'app-tableau-de-bord',
  imports: [RouterLink, DatePipe, ClientsArchivesComponent, AlerteActionsComponent],
  template: `
    <div class="page">
      <div class="entete-page">
        <div>
          <h1>Clients</h1>
          <p class="sous-titre">Publications du mois en cours</p>
        </div>
        <a class="bouton" routerLink="/clients/nouveau">+ Nouveau client</a>
      </div>

      @if (abonnement(); as a) {
        @if (a.statut === 'ESSAI') {
          <div class="info">Essai gratuit jusqu'au {{ a.fin | date: 'd MMMM' }} : {{ a.maxClients }} client, {{ a.calendriersParMois }} calendrier IA.</div>
        } @else if (a.statut === 'GRACE') {
          <div class="erreur">Ton abonnement a expiré le {{ a.fin | date: 'd MMMM' }}. Renouvelle-le pour éviter le passage en lecture seule.</div>
        } @else if (a.statut === 'LECTURE_SEULE') {
          <div class="erreur">Abonnement expiré : ton espace est en lecture seule.</div>
        }
      }
      @if (erreur()) { <div class="erreur">{{ erreur() }}</div> }
      @if (erreurReseaux) { <div class="erreur">Connexion Facebook impossible : {{ erreurReseaux }}</div> }

      @if (alertes().length) {
        <section class="carte surveillance" aria-label="À surveiller">
          <h2>À surveiller <span class="pastille statut-A_MODIFIER">{{ alertes().length }}</span></h2>
          @for (a of alertes(); track (a.publicationId ?? a.clientId) + a.type + a.reseau) {
            <div class="alerte niveau-{{ a.niveau }}">
              <a class="lien-alerte" [routerLink]="a.publicationId ? ['/publications', a.publicationId] : ['/clients', a.clientId]">
                <strong>{{ a.clientNom }}</strong>
                <span class="aide">{{ libelleReseau(a.reseau) }} · {{ a.date | date: 'd MMM' }}</span>
                <span class="texte">{{ a.message }}</span>
              </a>
              <app-alerte-actions [alerte]="a" [compteAReconnecter]="compteAReconnecter(a)" (termine)="rechargerAlertes()" />
            </div>
          }
        </section>
      }

      @if (clients() === null) {
        <p class="chargement">Chargement…</p>
      } @else if (clients()!.length === 0) {
        <div class="carte vide">
          <h2>Ajoute ton premier client</h2>
          <p>Sa fiche (description, offres, ton, couleurs, logos) sert de base à tous les contenus générés.</p>
          <a class="bouton" routerLink="/clients/nouveau">Créer une fiche client</a>
        </div>
      } @else {
        <div class="grille">
          @for (c of clients(); track c.id) {
            <a class="carte client" [routerLink]="['/clients', c.id]">
              <div class="ligne">
                @if (c.logoUrl) { <img [src]="c.logoUrl" alt="" class="logo" /> }
                @else { <span class="logo initiale">{{ c.nom.charAt(0) }}</span> }
                <div>
                  <h3>{{ c.nom }}</h3>
                  <p class="sous-titre">{{ c.secteur }}{{ c.secteur && c.ville ? ' · ' : '' }}{{ c.ville }}</p>
                </div>
              </div>
              <div class="compteurs">
                <span class="aide">Ce mois :</span>
                <span class="pastille statut-A_VALIDER">{{ c.aValider }} à valider</span>
                <span class="pastille statut-VALIDEE">{{ c.validees }} validées</span>
                @if (c.aModifier) { <span class="pastille statut-A_MODIFIER">{{ c.aModifier }} à modifier</span> }
              </div>
              <p class="aide infos-carte">
                @if (c.prochaine) { Prochaine publication : {{ c.prochaine | date: 'EEE d MMM' }} } @else { Rien de prévu pour l'instant }
                @if (c.derniere) { <br />Dernière publiée le {{ c.derniere | date: 'd MMM' }} }
              </p>
            </a>
          }
        </div>
      }
      <app-clients-archives (restaure)="recharger()" />
    </div>
  `,
  styles: `
    .infos-carte { margin: 8px 0 0; }
    .surveillance { border-left: 4px solid #dc2626; margin-bottom: 16px; }
    .alerte { padding: 10px 0; border-top: 1px solid var(--gris-clair); }
    .alerte:first-of-type { border-top: 0; }
    .lien-alerte { display: grid; gap: 2px; color: inherit; text-decoration: none; }
    .lien-alerte:hover .texte { text-decoration: underline; }
    .alerte.niveau-ATTENTION { border-left: 3px solid #d97706; padding-left: 8px; }
    .alerte.niveau-ERREUR { border-left: 3px solid #dc2626; padding-left: 8px; }
    .client { display: block; color: inherit; transition: transform .1s, box-shadow .15s; }
    .client:hover { text-decoration: none; box-shadow: 0 4px 12px rgba(17,24,39,.1); }
    .client h3 { margin: 0; }
    .logo { width: 44px; height: 44px; border-radius: 10px; object-fit: contain; background: #f9fafb; }
    .initiale { display: inline-flex; align-items: center; justify-content: center; font-weight: 700; font-size: 1.2rem; color: var(--vert-fonce); background: var(--vert-clair); }
    .compteurs { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 14px; }
  `,
})
export class TableauDeBordComponent {
  private api = inject(ApiService);
  clients = signal<ClientResume[] | null>(null);
  abonnement = signal<Abonnement | null>(null);
  erreur = signal('');
  alertes = signal<Alerte[]>([]);
  libelleReseau = (r: string) => LIBELLES_RESEAU[r] ?? r;
  erreurReseaux = new URLSearchParams(window.location.search).get('reseaux') === 'erreur'
    ? new URLSearchParams(window.location.search).get('message') ?? 'erreur inconnue' : '';

  /** Un compte du même client et du même réseau est à reconnecter : réessayer serait inutile tant qu'il ne l'est pas. */
  compteAReconnecter(a: Alerte) {
    return a.type !== 'JETON' && this.alertes().some(x => x.type === 'JETON' && x.clientId === a.clientId && x.reseau === a.reseau);
  }

  rechargerAlertes() {
    this.api.surveillance().subscribe({ next: a => this.alertes.set(a), error: () => {} });
    this.recharger();
  }

  recharger() {
    this.api.clients().subscribe({ next: c => this.clients.set(c), error: e => { this.erreur.set(messageErreur(e)); this.clients.set([]); } });
  }

  constructor() {
    this.recharger();
    this.api.abonnement().subscribe({ next: a => this.abonnement.set(a), error: () => {} });
    this.api.surveillance().subscribe({ next: a => this.alertes.set(a), error: () => {} });
  }
}
