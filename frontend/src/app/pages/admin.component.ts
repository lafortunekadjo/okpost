import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, messageErreur } from '../core/api.service';
import { ConfirmationService } from '../core/confirmation.service';
import { AdminCompte, AdminFormule, AdminJournal, AdminTotaux } from '../core/models';

type Onglet = 'comptes' | 'formules' | 'journal';
type Filtre = '' | 'essais' | 'payants' | 'bientot' | 'expires' | 'limites';

const STATUTS: Record<string, string> = { ESSAI: 'Essai', ACTIF: 'Actif', GRACE: 'Grâce : paiement attendu', LECTURE_SEULE: 'Lecture seule' };

/** Espace du propriétaire de la plateforme : suivi des comptes et de leur abonnement, formules, consommation d'IA et journal « qui a fait quoi ». */
@Component({
  selector: 'app-admin',
  imports: [DatePipe, FormsModule],
  template: `
    <div class="page">
      <h1>Administration</h1>
      <p class="aide">Vue d'ensemble de tous les comptes de la plateforme. Visible uniquement par le super-admin.</p>
      @if (erreur()) { <div class="erreur" role="alert">{{ erreur() }}</div> }
      @if (message()) { <div class="succes" role="status">{{ message() }}</div> }

      @if (totaux(); as t) {
        <div class="chiffres">
          <div class="carte"><strong>{{ t.comptes }}</strong><span>comptes</span></div>
          <div class="carte"><strong>{{ t.essais }}</strong><span>en essai</span></div>
          <div class="carte"><strong>{{ t.payants }}</strong><span>abonnés payants</span></div>
          <div class="carte"><strong>{{ fcfa(t.revenuMensuelXaf) }}</strong><span>revenu mensuel estimé</span></div>
          <div class="carte" [class.alerte]="t.expirentBientot > 0"><strong>{{ t.expirentBientot }}</strong><span>expirent sous 7 jours</span></div>
          <div class="carte" [class.alerte]="t.expires > 0"><strong>{{ t.expires }}</strong><span>expirés (grâce ou lecture seule)</span></div>
          <div class="carte"><strong>{{ t.clients }}</strong><span>clients actifs</span></div>
          <div class="carte"><strong>{{ t.publieesMois }}</strong><span>publiées ce mois</span></div>
          <div class="carte"><strong>{{ t.appelsIaMois }}</strong><span>appels IA ce mois @if (t.echecsIaMois) { <em>({{ t.echecsIaMois }} en échec)</em> }</span></div>
          <div class="carte"><strong>{{ t.imagesMois }}</strong><span>images IA ce mois</span></div>
          <div class="carte" [class.alerte]="t.echecsPublication7j > 0"><strong>{{ t.echecsPublication7j }}</strong><span>échecs de publication (7 j)</span></div>
        </div>
        <p class="aide">Le revenu estimé additionne le prix mensuel des abonnements payants actifs. Vos propres espaces ne sont pas comptés. Les paiements ne sont pas encore automatisés : les formules s'attribuent ici.</p>
      }

      <div class="onglets" role="tablist">
        <button type="button" role="tab" [class.actif]="onglet() === 'comptes'" [attr.aria-selected]="onglet() === 'comptes'" (click)="onglet.set('comptes')">Comptes</button>
        <button type="button" role="tab" [class.actif]="onglet() === 'formules'" [attr.aria-selected]="onglet() === 'formules'" (click)="onglet.set('formules')">Formules</button>
        <button type="button" role="tab" [class.actif]="onglet() === 'journal'" [attr.aria-selected]="onglet() === 'journal'" (click)="ouvrirJournal(null)">Journal d'activité</button>
      </div>

      @if (onglet() === 'comptes') {
        <div class="ligne filtres">
          <select [(ngModel)]="filtreComptes" aria-label="Filtrer les comptes">
            <option value="">Tous les comptes</option>
            <option value="essais">En essai</option>
            <option value="payants">Abonnés payants</option>
            <option value="bientot">Expirent sous 7 jours</option>
            <option value="expires">Expirés (grâce ou lecture seule)</option>
            <option value="limites">Proches d'une limite</option>
          </select>
          <input type="search" [(ngModel)]="recherche" placeholder="Chercher un compte ou un e-mail…" aria-label="Chercher un compte" class="recherche" />
          <span class="espace"></span>
          <span class="aide">{{ comptesFiltres().length }} compte(s)</span>
        </div>
        <div class="carte defile">
          <table>
            <thead>
              <tr>
                <th>Compte</th><th>Abonnement</th><th>Usage (limites de la formule)</th><th>Activité</th><th></th>
              </tr>
            </thead>
            <tbody>
              @for (c of comptesFiltres(); track c.id) {
                <tr [class.rouge]="c.statutAbonnement === 'GRACE' || c.statutAbonnement === 'LECTURE_SEULE'">
                  <td>
                    <strong>{{ c.nom }}</strong> @if (c.interne) { <span class="pastille">Votre espace</span> }<br />
                    <span class="aide">{{ c.emailAdmin }}</span><br />
                    <span class="aide">créé le {{ c.creeLe | date: 'd MMM y' }}</span>
                  </td>
                  <td>
                    <strong>{{ c.nomFormule || c.formule || '—' }}</strong>
                    @if (c.statutAbonnement) { <span class="pastille statut-{{ c.statutAbonnement === 'ACTIF' || c.statutAbonnement === 'ESSAI' ? 'VALIDEE' : 'A_MODIFIER' }}">{{ statut(c.statutAbonnement) }}</span> }<br />
                    @if (c.finAbonnement) {
                      <span class="aide">jusqu'au {{ c.finAbonnement | date: 'd MMM y' }}
                        @if (c.joursRestants !== null && c.joursRestants !== undefined && c.joursRestants < 400) { ({{ c.joursRestants >= 0 ? 'dans ' + c.joursRestants + ' j' : 'dépassé de ' + (-c.joursRestants) + ' j' }}) }</span>
                    }
                    @if (c.modeAbonnement && c.modeAbonnement !== 'ESSAI') { <br /><span class="aide">règlement : {{ mode(c.modeAbonnement) }}</span> }
                  </td>
                  <td class="usage">
                    @if (c.maxClients) {
                      <div class="ligne-usage"><span>Clients</span><div class="jauge"><div [class]="classeJauge(c.clients, c.maxClients)" [style.width.%]="pct(c.clients, c.maxClients)"></div></div><span>{{ c.clients }}/{{ c.maxClients }}</span></div>
                      <div class="ligne-usage"><span>Membres</span><div class="jauge"><div [class]="classeJauge(c.membres, c.maxMembres)" [style.width.%]="pct(c.membres, c.maxMembres)"></div></div><span>{{ c.membres }}/{{ c.maxMembres }}</span></div>
                      <div class="ligne-usage"><span>Calendriers</span><div class="jauge"><div [class]="classeJauge(c.calendriersMois, c.maxCalendriers)" [style.width.%]="pct(c.calendriersMois, c.maxCalendriers)"></div></div><span>{{ c.calendriersMois }}/{{ c.maxCalendriers }}</span></div>
                      <div class="ligne-usage"><span>Images IA</span><div class="jauge"><div [class]="classeJauge(c.imagesMois, c.maxImages)" [style.width.%]="pct(c.imagesMois, c.maxImages)"></div></div><span>{{ c.imagesMois }}/{{ c.maxImages }}</span></div>
                    } @else { <span class="aide">—</span> }
                  </td>
                  <td class="nowrap">
                    {{ c.publiees }} publiée(s) / {{ c.publications }}<br />
                    <span class="aide">{{ c.appelsIaMois }} appels IA ce mois</span><br />
                    <span class="aide">
                      @if (c.derniereConnexion) { connecté le {{ c.derniereConnexion | date: 'd MMM, HH:mm' }} } @else { jamais connecté }
                    </span>
                  </td>
                  <td class="nowrap">
                    <button type="button" class="bouton petit" (click)="ouvrirEdition(c)">Abonnement</button>
                    <button type="button" class="bouton secondaire petit" (click)="ouvrirJournal(c.id)">Activité</button>
                  </td>
                </tr>
                @if (edition()?.compte?.id === c.id) {
                  <tr class="edition">
                    <td colspan="5">
                      @if (edition(); as e) {
                        <div class="ligne editeur">
                          <label>Formule
                            <select [(ngModel)]="e.formule" aria-label="Formule du compte">
                              @for (f of formules(); track f.code) { <option [value]="f.code">{{ f.nom }} · {{ fcfa(f.prixMensuelXaf) }}/mois</option> }
                            </select>
                          </label>
                          <label>Valable jusqu'au <input type="date" [(ngModel)]="e.fin" aria-label="Date de fin de l'abonnement" /></label>
                          <div class="raccourcis">
                            <button type="button" class="bouton secondaire petit" (click)="prolonger(e, 30)">+30 jours</button>
                            <button type="button" class="bouton secondaire petit" (click)="prolonger(e, 90)">+3 mois</button>
                            <button type="button" class="bouton secondaire petit" (click)="prolonger(e, 365)">+1 an</button>
                            <button type="button" class="bouton secondaire petit" (click)="suspendre(e)" title="Date de fin dépassée de plus de 5 jours : le compte passe en lecture seule">Suspendre</button>
                          </div>
                        </div>
                        <p class="aide">{{ resumeEdition(e) }}</p>
                        <div class="ligne" style="gap:8px">
                          <button type="button" class="bouton" [disabled]="occupe() || !e.fin" (click)="enregistrerAbonnement(e)">{{ occupe() ? 'Enregistrement…' : 'Enregistrer' }}</button>
                          <button type="button" class="bouton secondaire" (click)="edition.set(null)">Annuler</button>
                        </div>
                      }
                    </td>
                  </tr>
                }
              } @empty {
                <tr><td colspan="5" class="aide">{{ chargement() ? 'Chargement…' : 'Aucun compte pour ce filtre.' }}</td></tr>
              }
            </tbody>
          </table>
        </div>
      }

      @if (onglet() === 'formules') {
        <p class="aide">Modifie le nom, le prix et les limites de chaque formule. <strong>Le changement s'applique tout de suite à tous les comptes qui l'ont.</strong>
          Un compte qui dépasse déjà une limite plus basse garde ses données, mais ne peut plus en créer. Le prix annuel est calculé automatiquement (10 mois pour 12).</p>
        <div class="formules">
          @for (f of formulesEdit(); track f.code) {
            <div class="carte formule">
              <div class="ligne"><h3>{{ f.code }}</h3><span class="espace"></span><span class="aide">{{ f.comptes }} compte(s)</span></div>
              <label>Nom <input type="text" [(ngModel)]="f.nom" maxlength="40" /></label>
              <label>Prix mensuel (FCFA) <input type="number" min="0" step="500" [(ngModel)]="f.prixMensuelXaf" [disabled]="f.code === 'ESSAI'" /></label>
              <label>Clients actifs <input type="number" min="1" [(ngModel)]="f.maxClients" /></label>
              <label>Membres <input type="number" min="1" [(ngModel)]="f.maxMembres" /></label>
              <label>Calendriers IA par mois <input type="number" min="1" [(ngModel)]="f.calendriersParMois" /></label>
              <label>Images IA par mois <input type="number" min="0" [(ngModel)]="f.imagesParMois" /></label>
              @if (f.code !== 'ESSAI') { <p class="aide">Annuel : {{ fcfa(f.prixMensuelXaf * 10) }}</p> } @else { <p class="aide">L'essai est toujours gratuit.</p> }
              <button type="button" class="bouton" [disabled]="occupe()" (click)="enregistrerFormule(f)">Enregistrer {{ f.code }}</button>
            </div>
          }
        </div>
      }

      @if (onglet() === 'journal') {
        <div class="carte">
          <div class="ligne filtres">
            <select [(ngModel)]="filtreEspace" (ngModelChange)="filtrer()" aria-label="Filtrer par compte">
              <option value="">Tous les comptes</option>
              @for (c of comptes(); track c.id) { <option [value]="c.id">{{ c.nom }}</option> }
            </select>
            <select [(ngModel)]="filtreAction" (ngModelChange)="filtrer()" aria-label="Filtrer par action">
              <option value="">Toutes les actions</option>
              @for (a of listeActions(); track a[0]) { <option [value]="a[0]">{{ a[1] }}</option> }
            </select>
            <span class="espace"></span>
            <span class="aide">{{ journal()?.total ?? 0 }} ligne(s) · conservées 12 mois</span>
          </div>
          <div class="defile">
            <table>
              <thead><tr><th>Quand</th><th>Qui</th><th>Action</th><th>Sur quoi</th><th>Détail</th><th>Compte</th></tr></thead>
              <tbody>
                @for (l of journal()?.lignes ?? []; track $index) {
                  <tr [class.rouge]="l.action === 'PUBLICATION_ECHEC' || l.action === 'CONNEXION_ECHEC'">
                    <td class="nowrap">{{ l.date | date: 'd MMM, HH:mm:ss' }}</td>
                    <td>{{ l.auteur }}</td>
                    <td>{{ l.libelle }}</td>
                    <td>{{ l.cible }}</td>
                    <td class="detail">{{ l.detail }}</td>
                    <td>{{ l.espace }}</td>
                  </tr>
                } @empty {
                  <tr><td colspan="6" class="aide">{{ chargement() ? 'Chargement…' : 'Aucune activité pour ce filtre.' }}</td></tr>
                }
              </tbody>
            </table>
          </div>
          @if (journal(); as j) {
            <div class="ligne pagination">
              <button type="button" class="bouton secondaire petit" [disabled]="j.page === 0" (click)="aller(j.page - 1)">Précédent</button>
              <span class="aide">Page {{ j.page + 1 }} sur {{ pages(j.total, j.taille) }}</span>
              <button type="button" class="bouton secondaire petit" [disabled]="(j.page + 1) * j.taille >= j.total" (click)="aller(j.page + 1)">Suivant</button>
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: `
    .chiffres { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 10px; margin: 14px 0 6px; }
    .chiffres .carte { display: flex; flex-direction: column; gap: 2px; padding: 12px 14px; }
    .chiffres strong { font-size: 1.4rem; }
    .chiffres span { color: var(--gris); font-size: .8rem; }
    .chiffres .alerte { border-left: 4px solid var(--rouge, #dc2626); }
    .onglets { display: flex; gap: 4px; margin: 16px 0 10px; }
    .onglets button { border: 0; background: transparent; padding: 8px 14px; border-radius: 8px; font-weight: 600; color: var(--gris); cursor: pointer; }
    .onglets button.actif { background: var(--vert-clair); color: var(--vert-fonce); }
    .defile { overflow-x: auto; }
    table { width: 100%; border-collapse: collapse; font-size: .85rem; }
    th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid var(--gris-clair); vertical-align: top; }
    th { color: var(--gris); font-weight: 600; white-space: nowrap; }
    .nowrap { white-space: nowrap; }
    .detail { max-width: 320px; overflow-wrap: anywhere; }
    tr.rouge td { background: #fef2f2; }
    tr.edition td { background: #f0fdf4; }
    .filtres { gap: 8px; margin-bottom: 10px; flex-wrap: wrap; }
    .recherche { max-width: 260px; }
    .pagination { justify-content: center; gap: 12px; margin-top: 12px; }
    .usage { min-width: 250px; }
    .ligne-usage { display: grid; grid-template-columns: 78px 1fr 48px; gap: 8px; align-items: center; font-size: .8rem; margin-bottom: 3px; }
    .jauge { height: 7px; background: #e5e7eb; border-radius: 4px; overflow: hidden; }
    .jauge div { height: 100%; background: #16a34a; }
    .jauge .orange { background: #d97706; }
    .jauge .rouge-fort { background: #dc2626; }
    .editeur { gap: 14px; flex-wrap: wrap; align-items: flex-end; margin-bottom: 6px; }
    .editeur label { display: grid; gap: 4px; font-weight: 600; font-size: .8rem; }
    .raccourcis { display: flex; gap: 6px; flex-wrap: wrap; }
    .formules { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 14px; }
    .formule { display: grid; gap: 8px; align-content: start; }
    .formule h3 { margin: 0; }
    .formule label { display: grid; gap: 4px; font-weight: 600; font-size: .8rem; }
  `,
})
export class AdminComponent implements OnInit {
  private api = inject(ApiService);
  private confirmation = inject(ConfirmationService);

  onglet = signal<Onglet>('comptes');
  comptes = signal<AdminCompte[]>([]);
  totaux = signal<AdminTotaux | null>(null);
  formules = signal<AdminFormule[]>([]);
  /** Copies modifiables des formules (le serveur n'est appelé qu'à l'enregistrement). */
  formulesEdit = signal<AdminFormule[]>([]);
  journal = signal<AdminJournal | null>(null);
  edition = signal<{ compte: AdminCompte; formule: string; fin: string } | null>(null);
  erreur = signal('');
  message = signal('');
  chargement = signal(true);
  occupe = signal(false);
  filtreComptes = signal<Filtre>('');
  recherche = signal('');
  filtreEspace = '';
  filtreAction = '';


  comptesFiltres = computed(() => {

    const filtre = this.filtreComptes();
    const q = this.recherche().trim().toLowerCase();
    return this.comptes().filter(c => {
      if (q && !(c.nom.toLowerCase().includes(q) || (c.emailAdmin ?? '').toLowerCase().includes(q))) return false;
      const actif = c.statutAbonnement === 'ESSAI' || c.statutAbonnement === 'ACTIF';
      switch (filtre) {
        case 'essais': return !c.interne && c.statutAbonnement === 'ESSAI';
        case 'payants': return !c.interne && c.formule !== 'ESSAI' && c.statutAbonnement === 'ACTIF';
        case 'bientot': return !c.interne && actif && c.joursRestants != null && c.joursRestants >= 0 && c.joursRestants <= 7;
        case 'expires': return !c.interne && (c.statutAbonnement === 'GRACE' || c.statutAbonnement === 'LECTURE_SEULE');
        case 'limites': return c.procheLimite;
        default: return true;
      }
    });
  });

  ngOnInit() {
    this.charger();
  }

  charger() {
    this.api.adminComptes().subscribe({
      next: v => { this.comptes.set(v.comptes); this.totaux.set(v.totaux); this.chargement.set(false); },
      error: e => { this.erreur.set(messageErreur(e)); this.chargement.set(false); },
    });
    this.api.adminFormules().subscribe({
      next: l => { this.formules.set(l); this.formulesEdit.set(l.map(f => ({ ...f }))); },
      error: e => this.erreur.set(messageErreur(e)),
    });
  }

  // ---------- affichage

  fcfa(n: number) {
    return `${Math.round(n).toLocaleString('fr-FR')} FCFA`;
  }

  statut(s: string) {
    return STATUTS[s] ?? s;
  }

  mode(m: string) {
    return ({ ESPECES: 'manuel / espèces', FLUTTERWAVE: 'Flutterwave', NOTCHPAY: 'Notch Pay' } as Record<string, string>)[m] ?? m;
  }

  pct(usage: number, max: number) {
    return max > 0 ? Math.min(100, (usage / max) * 100) : 0;
  }

  classeJauge(usage: number, max: number) {
    const p = max > 0 ? usage / max : 0;
    return p >= 1 ? 'rouge-fort' : p >= 0.8 ? 'orange' : '';
  }

  // ---------- abonnement d'un compte

  ouvrirEdition(c: AdminCompte) {
    this.message.set('');
    this.erreur.set('');
    this.edition.set({ compte: c, formule: c.formule ?? 'ESSAI', fin: c.finAbonnement ?? this.dansJours(14) });
  }

  private dansJours(n: number, depuis = new Date()) {
    const d = new Date(depuis);
    d.setDate(d.getDate() + n);
    const p = (x: number) => String(x).padStart(2, '0');
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
  }

  /** Prolonge à partir de la fin actuelle si elle est dans le futur, sinon à partir d'aujourd'hui. */
  prolonger(e: { fin: string }, jours: number) {
    const aujourdhui = this.dansJours(0);
    const base = e.fin && e.fin > aujourdhui ? new Date(e.fin + 'T12:00:00') : new Date();
    e.fin = this.dansJours(jours, base);
  }

  suspendre(e: { fin: string }) {
    e.fin = this.dansJours(-10);
  }

  resumeEdition(e: { compte: AdminCompte; formule: string; fin: string }) {
    const f = this.formules().find(x => x.code === e.formule);
    const avant = e.compte.formule === e.formule ? `reste en ${f?.nom ?? e.formule}` : `passe de ${e.compte.nomFormule ?? e.compte.formule ?? '—'} à ${f?.nom ?? e.formule}`;
    const passe = e.fin < this.dansJours(-5);
    return `${e.compte.nom} ${avant}, jusqu'au ${e.fin}.` + (passe ? ' Cette date est dépassée de plus de 5 jours : le compte sera en lecture seule.' : '')
      + (f ? ` Limites : ${f.maxClients} client(s), ${f.maxMembres} membre(s), ${f.calendriersParMois} calendrier(s) et ${f.imagesParMois} image(s) IA par mois.` : '');
  }

  enregistrerAbonnement(e: { compte: AdminCompte; formule: string; fin: string }) {
    this.occupe.set(true);
    this.erreur.set('');
    this.api.changerAbonnement(e.compte.id, { formule: e.formule, fin: e.fin }).subscribe({
      next: () => {
        this.occupe.set(false);
        this.message.set(`Abonnement de ${e.compte.nom} mis à jour.`);
        this.edition.set(null);
        this.charger();
      },
      error: err => { this.occupe.set(false); this.erreur.set(messageErreur(err)); },
    });
  }

  // ---------- formules

  async enregistrerFormule(f: AdminFormule) {
    const ancienne = this.formules().find(x => x.code === f.code);
    if (f.comptes > 0 && !(await this.confirmation.demander({
      titre: `Modifier la formule ${f.code} ?`,
      message: `Elle est utilisée par ${f.comptes} compte(s) : les nouvelles limites et le nouveau prix s'appliquent tout de suite à eux.`
        + (ancienne && (f.maxClients < ancienne.maxClients || f.imagesParMois < ancienne.imagesParMois || f.calendriersParMois < ancienne.calendriersParMois)
          ? ' Certains comptes dépassent peut-être déjà ces limites : ils garderont leurs données mais ne pourront plus en créer.' : ''),
      confirmer: 'Modifier la formule',
    }))) return;
    this.occupe.set(true);
    this.erreur.set('');
    this.message.set('');
    this.api.modifierFormule(f.code, {
      nom: f.nom, prixMensuelXaf: Number(f.prixMensuelXaf), maxClients: Number(f.maxClients), maxMembres: Number(f.maxMembres),
      calendriersParMois: Number(f.calendriersParMois), imagesParMois: Number(f.imagesParMois),
    }).subscribe({
      next: maj => {
        this.occupe.set(false);
        this.message.set(`Formule ${maj.code} mise à jour.`);
        this.charger();
      },
      error: e => { this.occupe.set(false); this.erreur.set(messageErreur(e)); },
    });
  }

  // ---------- journal

  listeActions(): [string, string][] {
    return Object.entries(this.journal()?.actions ?? {});
  }

  ouvrirJournal(espaceId: string | null) {
    this.onglet.set('journal');
    this.filtreEspace = espaceId ?? '';
    this.filtreAction = '';
    this.aller(0);
  }

  filtrer() {
    this.aller(0);
  }

  aller(page: number) {
    this.chargement.set(true);
    this.api.adminJournal({ espace: this.filtreEspace || undefined, action: this.filtreAction || undefined, page }).subscribe({
      next: j => { this.journal.set(j); this.chargement.set(false); },
      error: e => { this.erreur.set(messageErreur(e)); this.chargement.set(false); },
    });
  }

  pages(total: number, taille: number) {
    return Math.max(1, Math.ceil(total / taille));
  }
}
