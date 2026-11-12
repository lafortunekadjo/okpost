import { Component, inject, input, signal, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ApiService, messageErreur } from '../core/api.service';
import { CalendrierResume, Client, CompteSocial, LIBELLES_RESEAU } from '../core/models';
import { BanqueImagesComponent } from '../shared/banque-images.component';

@Component({
  selector: 'app-client',
  imports: [RouterLink, DatePipe, FormsModule, BanqueImagesComponent],
  template: `
    <div class="page">
      @if (erreur()) { <div class="erreur">{{ erreur() }}</div> }
      @if (client(); as c) {
        <div class="entete-page">
          <div class="ligne">
            @if (logo()) { <img [src]="logo()" alt="" class="logo" /> }
            <div>
              <h1>{{ c.nom }}</h1>
              <p class="sous-titre">{{ c.secteur }}{{ c.secteur && c.ville ? ' · ' : '' }}{{ c.ville }}</p>
            </div>
          </div>
          <div class="ligne">
            <a class="bouton secondaire" [routerLink]="['/clients', c.id, 'fiche']">Modifier la fiche</a>
            @if (comptes().length) { <a class="bouton secondaire" [routerLink]="['/clients', c.id, 'bilan']">Bilan Facebook</a> }
            <a class="bouton" [routerLink]="['/clients', c.id, 'calendriers', 'nouveau']">+ Nouveau calendrier</a>
          </div>
        </div>

        @if (manques().length) {
          <div class="info">Pour de meilleurs contenus, complète la fiche : {{ manques().join(', ') }}.</div>
        }

        <div class="carte">
          <h2>Calendriers</h2>
          @if (calendriers() === null) {
            <p class="chargement">Chargement…</p>
          } @else if (calendriers()!.length === 0) {
            <p class="vide">Aucun calendrier pour l'instant. Génère le premier en 2 minutes.</p>
          } @else {
            <ul class="liste">
              @for (cal of calendriers(); track cal.id) {
                <li>
                  <a [routerLink]="['/calendriers', cal.id]">
                    <strong>{{ cal.debut | date: 'd MMM' }} – {{ cal.fin | date: 'd MMM y' }}</strong>
                    <span class="sous-titre">{{ cal.publications }} publications · {{ nomsReseaux(cal.reseaux) }}</span>
                  </a>
                  @if (cal.statutGeneration !== 'TERMINE') {
                    <span class="pastille" [class.statut-A_MODIFIER]="cal.statutGeneration === 'ERREUR'">
                      {{ cal.statutGeneration === 'ERREUR' ? 'Erreur' : 'En cours' }}</span>
                  }
                </li>
              }
            </ul>
          }
        </div>

        <app-banque-images [clientId]="c.id!" />

        <div class="carte">
          <h2>Réseaux connectés</h2>
          @if (messageReseaux()) { <div class="succes">{{ messageReseaux() }}</div> }
          @if (erreurReseaux()) { <div class="erreur">{{ erreurReseaux() }}</div> }
          <p class="aide">Relie la Page Facebook du client : son compte Instagram professionnel, s'il est lié à la Page, est relié en même temps.
            Les publications validées pourront se publier toutes seules sur les deux. TikTok et LinkedIn viendront ensuite.</p>
          @for (cpt of comptes(); track cpt.id) {
            <div class="ligne compte">
              <span class="reseau reseau-{{ cpt.reseau }}"></span>
              <strong class="espace">{{ cpt.nom }} <span class="aide">{{ cpt.reseau === 'INSTAGRAM' ? 'Instagram' : 'Facebook' }}</span></strong>
              @if (cpt.actif) { <span class="pastille statut-VALIDEE">Compte utilisé</span> }
              @else { <button class="bouton secondaire petit" (click)="activer(cpt.id)">Utiliser ce compte</button> }
              <button class="bouton danger petit" (click)="delier(cpt.id)">Délier</button>
            </div>
          }
          <div class="ligne" style="margin-top:12px">
            @if (connexionFacebook()) {
              <button class="bouton" (click)="connecterFacebook()">{{ comptes().length ? 'Relier une autre Page' : 'Relier la Page Facebook' }}</button>
            }
            <button class="bouton secondaire petit" (click)="modeJeton.set(!modeJeton())">Avec un jeton (avancé)</button>
          </div>
          @if (modeJeton()) {
            <div class="champ" style="margin-top:12px">
              <label>Jeton d'accès <span class="aide">jeton utilisateur prolongé (pages_show_list, pages_read_engagement, pages_manage_posts, read_insights, pages_read_user_content, instagram_basic, instagram_content_publish) ou jeton de Page</span></label>
              <div class="ligne"><input type="password" [(ngModel)]="jeton" autocomplete="off" class="espace" />
                <button class="bouton" [disabled]="!jeton" (click)="relierJeton()">Relier</button></div>
              <p class="aide">Le jeton est chiffré sur le serveur et n'est plus jamais affiché.</p>
            </div>
          }
          <hr />
          <label class="case"><input type="checkbox" [(ngModel)]="auto" (change)="enregistrerAuto(c)" />
            Publier automatiquement après validation par le client</label>
          <div class="ligne" style="margin-top:8px">
            <label style="margin:0">Heure de publication par défaut</label>
            <input type="time" [(ngModel)]="heure" (change)="enregistrerAuto(c)" style="max-width:130px" />
            <span class="aide">heure de Douala</span>
          </div>
        </div>

        <div class="ligne" style="margin-top:24px">
          <span class="espace"></span>
          <button class="bouton danger petit" (click)="archiver(c)">Archiver ce client</button>
        </div>
      }
    </div>
  `,
  styles: `
    .logo { width: 52px; height: 52px; border-radius: 12px; object-fit: contain; background: #fff; box-shadow: var(--ombre); }
    .liste { list-style: none; margin: 0; padding: 0; }
    .liste li { display: flex; align-items: center; gap: 12px; padding: 12px 0; border-top: 1px solid var(--gris-clair); }
    .liste li:first-child { border-top: none; }
    .liste a { flex: 1; display: flex; flex-direction: column; color: inherit; }
    .carte + .carte { margin-top: 16px; }
    .compte { padding: 8px 0; border-top: 1px solid var(--gris-clair); }
    .case { display: flex; gap: 8px; align-items: center; font-weight: 500; }
    hr { border: none; border-top: 1px solid var(--gris-clair); margin: 16px 0; }
  `,
})
export class ClientComponent implements OnInit {
  private api = inject(ApiService);
  private router = inject(Router);
  id = input.required<string>();
  client = signal<Client | null>(null);
  calendriers = signal<CalendrierResume[] | null>(null);
  logo = signal<string | null>(null);
  manques = signal<string[]>([]);
  erreur = signal('');
  comptes = signal<CompteSocial[]>([]);
  connexionFacebook = signal(false);
  modeJeton = signal(false);
  messageReseaux = signal('');
  erreurReseaux = signal('');
  jeton = '';
  auto = false;
  heure = '18:00';
  private route = inject(ActivatedRoute);

  ngOnInit() {
    if (this.route.snapshot.queryParamMap.get('reseaux') === 'ok') this.messageReseaux.set('Page Facebook reliée.');
    this.chargerComptes();
    this.api.client(this.id()).subscribe({
      next: c => {
        this.client.set(c);
        this.auto = !!c.autoPublication;
        this.heure = c.heurePublication ?? '18:00';
        this.logo.set(c.assets?.find(a => a.type === 'LOGO_COULEUR' || a.type === 'ICONE')?.url ?? null);
        const m: string[] = [];
        if (!c.description) m.push('description');
        if (!c.offres?.length) m.push('offres');
        if (!c.cibles?.length) m.push('cibles');
        if (!c.ton) m.push('ton');
        if (!c.couleurs?.length) m.push('couleurs');
        this.manques.set(m);
      },
      error: e => this.erreur.set(messageErreur(e)),
    });
    this.api.calendriers(this.id()).subscribe({ next: c => this.calendriers.set(c), error: () => this.calendriers.set([]) });
  }

  chargerComptes() {
    this.api.comptesSociaux(this.id()).subscribe({
      next: r => { this.comptes.set(r.comptes); this.connexionFacebook.set(r.connexionFacebookDisponible); },
      error: () => {},
    });
  }

  connecterFacebook() {
    this.api.urlConnexionFacebook(this.id()).subscribe({
      next: r => (window.location.href = r.url),
      error: e => this.erreurReseaux.set(messageErreur(e)),
    });
  }

  relierJeton() {
    this.erreurReseaux.set('');
    this.api.relierFacebookJeton(this.id(), this.jeton).subscribe({
      next: l => { this.comptes.set(l); this.jeton = ''; this.modeJeton.set(false); this.messageReseaux.set('Page Facebook reliée.'); },
      error: e => this.erreurReseaux.set(messageErreur(e)),
    });
  }

  activer(compteId: string) {
    this.api.activerCompte(this.id(), compteId).subscribe({ next: l => this.comptes.set(l), error: e => this.erreurReseaux.set(messageErreur(e)) });
  }

  delier(compteId: string) {
    if (!confirm('Délier cette Page ? Les publications programmées ne pourront plus partir.')) return;
    this.api.supprimerCompte(this.id(), compteId).subscribe({
      next: () => this.comptes.update(l => l.filter(x => x.id !== compteId)),
      error: e => this.erreurReseaux.set(messageErreur(e)),
    });
  }

  enregistrerAuto(c: Client) {
    const donnees: Client = { ...c, autoPublication: this.auto, heurePublication: this.heure };
    delete donnees.assets;
    this.api.modifierClient(c.id!, donnees).subscribe({
      next: maj => { this.client.set({ ...c, ...maj }); this.messageReseaux.set('Réglage enregistré.'); setTimeout(() => this.messageReseaux.set(''), 2000); },
      error: e => this.erreurReseaux.set(messageErreur(e)),
    });
  }

  nomsReseaux(r: string[]) {
    return r.map(x => LIBELLES_RESEAU[x] ?? x).join(', ');
  }

  archiver(c: Client) {
    if (!confirm(`Archiver ${c.nom} ? La fiche et ses calendriers ne seront plus affichés.`)) return;
    this.api.archiverClient(c.id!).subscribe({ next: () => this.router.navigateByUrl('/'), error: e => this.erreur.set(messageErreur(e)) });
  }
}
