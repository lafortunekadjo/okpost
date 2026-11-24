import { Component, computed, inject, input, signal, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, messageErreur } from '../core/api.service';
import { LIBELLES_FORMAT, LIBELLES_RESEAU, Portail, PortailPublication } from '../core/models';
import { LogoComponent } from '../shared/logo.component';

type Mode = 'lecture' | 'texte' | 'commentaire';

@Component({
  selector: 'app-portail',
  imports: [DatePipe, FormsModule, LogoComponent],
  template: `
    @if (erreur() && !portail()) {
      <div class="page page-etroite"><div class="carte vide"><h2>Lien indisponible</h2><p>{{ erreur() }}</p></div></div>
    }
    @if (portail(); as p) {
      @if (p.codeRequis && !deverrouille()) {
        <header class="bandeau" [style.background]="couleur()">
          <div class="contenu">
            @if (p.logoUrl) { <img [src]="p.logoUrl" alt="" class="logo" /> }
            <div><h1>{{ p.clientNom }}</h1><p>Validation des publications</p></div>
          </div>
        </header>
        <div class="page page-etroite">
          <form class="carte" (ngSubmit)="deverrouiller()">
            <h2>Entrez votre code personnel</h2>
            <p class="aide">Ce code à 6 chiffres vous a été remis par votre community manager. Il permet d'enregistrer que c'est bien vous qui validez.</p>
            @if (erreur()) { <div class="erreur" role="alert">{{ erreur() }}</div> }
            <div class="champ"><label for="code">Code</label>
              <input id="code" name="code" type="text" inputmode="numeric" autocomplete="one-time-code" maxlength="6" pattern="[0-9]{6}"
                     [(ngModel)]="saisieCode" style="max-width:220px;font-size:1.4rem;letter-spacing:.3em" required /></div>
            <button class="bouton" [disabled]="envoi() || saisieCode.length !== 6">{{ envoi() ? 'Vérification…' : 'Accéder aux publications' }}</button>
          </form>
        </div>
      } @else {
      <header class="bandeau" [style.background]="couleur()">
        <div class="contenu">
          @if (p.logoUrl) { <img [src]="p.logoUrl" alt="" class="logo" /> }
          <div>
            <h1>{{ p.clientNom }}</h1>
            <p>Publications du {{ p.debut | date: 'd MMMM' }} au {{ p.fin | date: 'd MMMM' }}@if (p.validateur) { · connecté : {{ p.validateur }} }</p>
          </div>
        </div>
      </header>
      <div class="page page-etroite">
        <div class="carte resume">
          <strong>{{ validees() }} sur {{ p.total }} validées</strong>
          @if (aModifier()) { <span class="pastille statut-A_MODIFIER">{{ aModifier() }} à modifier</span> }
          <div class="barre"><div [style.width.%]="(validees() / p.total) * 100"></div></div>
          <p class="aide">Pour chaque publication : validez, corrigez le texte vous-même, ou expliquez ce qu'il faut changer.</p>
        </div>
        @if (erreur()) { <div class="erreur">{{ erreur() }}</div> }

        @for (pub of p.publications; track pub.id) {
          <article class="carte publi" [class.ok]="pub.statut === 'VALIDEE'" [class.modif]="pub.statut === 'A_MODIFIER'">
            <div class="ligne">
              <strong>{{ pub.date | date: 'EEEE d MMMM' }}</strong>
              <span class="aide"><span class="reseau reseau-{{ pub.reseau }}"></span>{{ libellesReseau[pub.reseau] }} · {{ libellesFormat[pub.format] }}</span>
              <span class="espace"></span>
              @if (pub.statut === 'VALIDEE') { <span class="pastille statut-VALIDEE">Validée</span> }
              @if (pub.statut === 'A_MODIFIER') { <span class="pastille statut-A_MODIFIER">Modification demandée</span> }
            </div>

            @if (pub.format === 'VIDEO' && pub.visuelType === 'VIDEO' && pub.visuelUrl) {
              <video class="image-prevue" [src]="pub.visuelUrl" controls playsinline preload="metadata"></video>
            } @else if (pub.format === 'VIDEO' && pub.scenario) {
              <div class="visuel">
                <span class="aide">Vidéo prévue · {{ pub.scenario.dureeTotaleS }} s</span>
                <p><strong>{{ pub.scenario.titre }}</strong></p>
                @if (pub.scenario.accroche) { <p class="aide">Accroche : {{ pub.scenario.accroche }}</p> }
                <details><summary class="aide">Voir le scénario</summary>
                  <ol>@for (pl of pub.scenario.plans; track pl.numero) { <li>{{ pl.image }} @if (pl.texteEcran) { — « {{ pl.texteEcran }} » }</li> }</ol>
                </details>
              </div>
            } @else if (pub.visuelUrl) {
              @if (pub.visuelType === 'VIDEO') {
                <video class="image-prevue" [src]="pub.visuelUrl" controls playsinline preload="metadata"></video>
              } @else {
                <img class="image-prevue" [src]="pub.visuelUrl" alt="Visuel prévu" loading="lazy" />
              }
              @if (pub.brief?.textesCarrousel?.length) {
                <div class="visuel"><ol>@for (t of pub.brief!.textesCarrousel; track $index) { <li><strong>{{ t.titre }}</strong> — {{ t.texte }}</li> }</ol></div>
              }
            } @else if (pub.brief) {
              <div class="visuel">
                <span class="aide">Visuel prévu</span>
                <p>{{ pub.brief.description }}</p>
                @if (pub.brief.textesCarrousel?.length) {
                  <ol>@for (t of pub.brief.textesCarrousel; track $index) { <li><strong>{{ t.titre }}</strong> — {{ t.texte }}</li> }</ol>
                }
              </div>
            }

            @if (mode(pub.id) === 'texte') {
              <textarea rows="8" [(ngModel)]="brouillons[pub.id]"></textarea>
              <div class="ligne boutons">
                <button class="bouton" [disabled]="envoi()" (click)="enregistrerTexte(pub)">Enregistrer et valider</button>
                <button class="bouton secondaire" (click)="modes.set(pub.id, 'lecture')">Annuler</button>
              </div>
            } @else {
              <p class="pre legende">{{ pub.legende }}</p>
              @if (pub.cta) { <p class="pre">{{ pub.cta }}</p> }
              @if (pub.hashtags?.length) { <p class="hashtags">{{ pub.hashtags!.join(' ') }}</p> }
            }

            @if (mode(pub.id) === 'commentaire') {
              <label>Que faut-il changer ?</label>
              <textarea rows="3" [(ngModel)]="commentaires[pub.id]" placeholder="Ex. utiliser une photo de la boutique, annoncer le prix promo"></textarea>
              <div class="ligne boutons">
                <button class="bouton" [disabled]="envoi() || !commentaires[pub.id]?.trim()" (click)="demander(pub)">Envoyer la demande</button>
                <button class="bouton secondaire" (click)="modes.set(pub.id, 'lecture')">Annuler</button>
              </div>
            } @else if (mode(pub.id) === 'lecture' && modifiable(pub)) {
              @if (pub.dernierCommentaire && pub.statut === 'A_MODIFIER') { <p class="aide">Votre demande : « {{ pub.dernierCommentaire }} »</p> }
              <div class="ligne boutons">
                @if (pub.statut !== 'VALIDEE') { <button class="bouton" [disabled]="envoi()" (click)="valider(pub)">✓ Valider</button> }
                <button class="bouton secondaire" (click)="editer(pub)">Modifier le texte</button>
                <button class="bouton secondaire" (click)="modes.set(pub.id, 'commentaire')">Demander une modification</button>
              </div>
            }
          </article>
        }

        <p class="pied"><app-logo [taille]="14" /> · lien valable jusqu'au {{ p.expireLe | date: 'd MMMM y' }}</p>
      </div>
      }
    }
  `,
  styles: `
    .bandeau { color: #fff; padding: 28px 16px 56px; }
    .bandeau .contenu { max-width: 720px; margin: 0 auto; display: flex; gap: 14px; align-items: center; }
    .bandeau h1 { margin: 0; color: #fff; }
    .bandeau p { margin: 0; opacity: .9; }
    .logo { width: 56px; height: 56px; border-radius: 12px; background: #fff; object-fit: contain; padding: 4px; }
    .page { margin-top: -40px; }
    .resume { display: grid; gap: 8px; margin-bottom: 16px; }
    .resume p { margin: 0; }
    .publi { border-left: 4px solid var(--gris-clair); }
    .publi.ok { border-left-color: var(--vert); }
    .publi.modif { border-left-color: var(--orange); }
    .visuel { background: #f9fafb; border-radius: 8px; padding: 10px 12px; margin: 12px 0; font-size: .9rem; }
    .visuel p { margin: 4px 0 0; }
    .visuel ol { margin: 6px 0 0; padding-left: 20px; }
    .legende { margin-top: 12px; }
    .image-prevue { width: 100%; border-radius: 10px; margin: 12px 0 0; display: block; }
    .hashtags { color: var(--bleu); font-size: .9rem; }
    .boutons { margin-top: 12px; }
    .pied { text-align: center; color: var(--gris); font-size: .8rem; margin-top: 24px; }
    @media (max-width: 640px) { .boutons .bouton { flex: 1 1 100%; } }
  `,
})
export class PortailComponent implements OnInit {
  private api = inject(ApiService);
  jeton = input.required<string>();

  portail = signal<Portail | null>(null);
  erreur = signal('');
  envoi = signal(false);
  modes = new Map<string, Mode>();
  saisieCode = '';
  private code: string | null = null;
  deverrouille = computed(() => !this.portail()?.codeRequis || !!this.portail()?.validateur);
  brouillons: Record<string, string> = {};
  commentaires: Partial<Record<string, string>> = {};
  libellesReseau = LIBELLES_RESEAU;
  libellesFormat = LIBELLES_FORMAT;

  couleur = computed(() => {
    const c = this.portail()?.couleur;
    return c && /^#[0-9a-fA-F]{6}$/.test(c) ? c : '#15803d';
  });
  validees = computed(() => this.portail()?.publications.filter(p => p.statut === 'VALIDEE').length ?? 0);
  aModifier = computed(() => this.portail()?.publications.filter(p => p.statut === 'A_MODIFIER').length ?? 0);

  ngOnInit() {
    // Le code est gardé le temps de la session du navigateur pour ne pas le redemander à chaque rechargement
    try { this.code = sessionStorage.getItem('code-validation-' + this.jeton()); } catch { /* stockage indisponible */ }
    this.charger();
  }

  deverrouiller() {
    this.code = this.saisieCode.trim();
    this.envoi.set(true);
    this.erreur.set('');
    this.charger();
  }

  charger() {
    this.api.portail(this.jeton(), this.code).subscribe({
      next: p => {
        this.portail.set(p);
        this.envoi.set(false);
        if (p.validateur && this.code) {
          try { sessionStorage.setItem('code-validation-' + this.jeton(), this.code); } catch { /* stockage indisponible */ }
        }
      },
      error: e => {
        this.envoi.set(false);
        this.erreur.set(messageErreur(e));
        if (this.code) {
          // Code refusé (faux, remplacé, retiré ou lien bloqué) : on l'oublie et on revient à l'écran de saisie
          this.code = null;
          try { sessionStorage.removeItem('code-validation-' + this.jeton()); } catch { /* ignoré */ }
          this.api.portail(this.jeton()).subscribe({ next: p => this.portail.set(p), error: () => { /* l'erreur reste affichée */ } });
        }
      },
    });
  }

  mode(id: string): Mode {
    return this.modes.get(id) ?? 'lecture';
  }

  modifiable(p: PortailPublication) {
    return ['A_VALIDER', 'VALIDEE', 'A_MODIFIER'].includes(p.statut);
  }

  editer(p: PortailPublication) {
    this.brouillons[p.id] = p.legende ?? '';
    this.modes.set(p.id, 'texte');
  }

  valider(p: PortailPublication) {
    this.envoi.set(true);
    this.api.decider(this.jeton(), p.id, 'OK', undefined, this.code).subscribe({ next: () => this.charger(), error: e => this.echec(e) });
  }

  demander(p: PortailPublication) {
    this.envoi.set(true);
    this.api.decider(this.jeton(), p.id, 'A_MODIFIER', this.commentaires[p.id], this.code).subscribe({
      next: () => { this.modes.set(p.id, 'lecture'); this.charger(); },
      error: e => this.echec(e),
    });
  }

  enregistrerTexte(p: PortailPublication) {
    this.envoi.set(true);
    this.api.modifierLegendePortail(this.jeton(), p.id, this.brouillons[p.id], undefined, this.code).subscribe({
      next: () => { this.modes.set(p.id, 'lecture'); this.charger(); },
      error: e => this.echec(e),
    });
  }

  private echec(e: unknown) {
    this.erreur.set(messageErreur(e));
    this.envoi.set(false);
  }
}
