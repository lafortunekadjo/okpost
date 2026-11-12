import { Component, inject, input, signal, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpEventType } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { ApiService, messageErreur } from '../core/api.service';
import {
  BriefVisuel, LIBELLES_FORMAT, LIBELLES_RESEAU, LIBELLES_STATUT, Publication, RESEAUX, ScenarioVideo, StatutPublication,
} from '../core/models';
import { PucesComponent } from '../shared/puces.component';

const DECISIONS: Partial<Record<string, string>> = { OK: 'Validée', A_MODIFIER: 'Modification demandée', MODIFIEE: 'Légende modifiée par le client' };

@Component({
  selector: 'app-publication',
  imports: [FormsModule, RouterLink, DatePipe, PucesComponent],
  template: `
    <div class="page page-etroite">
      @if (erreur()) { <div class="erreur">{{ erreur() }}</div> }
      @if (p(); as pub) {
        <div class="entete-page">
          <div>
            <a [routerLink]="['/calendriers', pub.calendrierId]" class="aide">← Calendrier</a>
            <h1>{{ pub.date | date: 'EEEE d MMMM' }}</h1>
            <p class="sous-titre"><span class="reseau reseau-{{ pub.reseau }}"></span>{{ libellesReseau[pub.reseau] }} · {{ libellesFormat[pub.format] }}
              @if (pub.pilier) { · {{ pub.pilier }} }</p>
          </div>
          <span class="pastille statut-{{ pub.statut }}">{{ libellesStatut[pub.statut] }}</span>
        </div>

        @if (pub.retours?.length) {
          <div class="carte retours">
            <h2>Retours du client</h2>
            @for (r of pub.retours; track $index) {
              <div class="retour">
                <strong>{{ decisions[r.decision] ?? r.decision }}</strong>
                <span class="aide"> · {{ r.date | date: 'd MMM, HH:mm' }}</span>
                @if (r.commentaire) { <p class="pre">« {{ r.commentaire }} »</p> }
                @if (r.legendeOrigine) {
                  <details><summary class="aide">Voir la légende d'origine</summary><p class="pre aide">{{ r.legendeOrigine }}</p></details>
                }
              </div>
            }
          </div>
        }

        @if (pub.reseau !== 'FACEBOOK' && pub.reseau !== 'INSTAGRAM' && pub.statut === 'VALIDEE') {
          <div class="info">Validée. La publication automatique n'est disponible que pour Facebook et Instagram pour l'instant : publie-la à la main sur {{ libellesReseau[pub.reseau] }}.</div>
        }
        @if ((pub.reseau === 'FACEBOOK' || pub.reseau === 'INSTAGRAM') && ['VALIDEE', 'PROGRAMMEE', 'PUBLIEE'].includes(pub.statut)) {
          <div class="carte publication-auto">
            @if (pub.statut === 'PUBLIEE') {
              <strong>Publiée sur {{ pub.reseau === 'INSTAGRAM' ? 'Instagram' : 'Facebook' }}</strong> le {{ pub.publieeLe | date: 'd MMMM à HH:mm' }}
              @if (pub.lienPost) { · <a [href]="pub.lienPost" target="_blank" rel="noopener">Voir la publication</a> }
            } @else if (pub.statut === 'PROGRAMMEE') {
              <div class="ligne">
                <span class="espace"><strong>Programmée</strong> pour le {{ pub.publicationPrevue | date: 'EEEE d MMMM à HH:mm' }}</span>
                <button class="bouton secondaire petit" [disabled]="actionAuto()" (click)="annulerProgrammation()">Annuler</button>
                <button class="bouton petit" [disabled]="actionAuto()" (click)="publierMaintenant()">Publier maintenant</button>
              </div>
              @if (pub.erreurPublication) { <p class="erreur" style="margin:8px 0 0">Dernier essai échoué : {{ pub.erreurPublication }}</p> }
            } @else {
              <div class="ligne">
                <span class="espace">Validée par le client. Publier sur {{ pub.reseau === 'INSTAGRAM' ? 'le compte Instagram relié' : 'la Page Facebook reliée' }} :</span>
                <input type="datetime-local" [(ngModel)]="quand" style="max-width:220px" />
                <button class="bouton secondaire petit" [disabled]="actionAuto()" (click)="programmer()">Programmer</button>
                <button class="bouton petit" [disabled]="actionAuto()" (click)="publierMaintenant()">Publier maintenant</button>
              </div>
              <p class="aide" style="margin:6px 0 0">Sans date choisie : jour prévu, à l'heure par défaut de la fiche client.</p>
            }
          </div>
        }

        @if (message()) { <div class="succes">{{ message() }}</div> }

        <form class="carte" (ngSubmit)="enregistrer()">
          <div class="ligne-champs">
            <div class="champ"><label>Date</label><input type="date" name="date" [(ngModel)]="pub.date" /></div>
            <div class="champ"><label>Réseau</label>
              <select name="reseau" [(ngModel)]="pub.reseau">@for (r of reseaux; track r) { <option [value]="r">{{ libellesReseau[r] }}</option> }</select></div>
            <div class="champ"><label>Format</label>
              <select name="format" [(ngModel)]="pub.format">@for (f of formats; track f) { <option [value]="f">{{ libellesFormat[f] }}</option> }</select></div>
            <div class="champ"><label>Statut</label>
              <select name="statut" [(ngModel)]="pub.statut">@for (s of statuts; track s) { <option [value]="s">{{ libellesStatut[s] }}</option> }</select></div>
          </div>
          <div class="champ"><label>Idée</label><textarea name="idee" rows="2" [(ngModel)]="pub.idee"></textarea></div>

          <div class="champ">
            <div class="ligne"><label class="espace">Légende <span class="aide">{{ (pub.legende || '').length }} caractères</span></label>
              <button type="button" class="bouton secondaire petit" (click)="copier(texteAPublier(pub), 'legende')">{{ copie() === 'legende' ? 'Copié' : 'Copier légende + hashtags' }}</button></div>
            <textarea name="legende" rows="8" [(ngModel)]="pub.legende"></textarea>
          </div>
          <div class="champ"><label>Hashtags</label><app-puces [(valeurs)]="hashtags" placeholder="#Douala puis Entrée" /></div>
          <div class="champ"><label>Appel à l'action</label><input type="text" name="cta" [(ngModel)]="pub.cta" /></div>

          @if (pub.format === 'VIDEO') {
            <h2>Scénario vidéo</h2>
            <div class="ligne-champs">
              <div class="champ"><label>Titre</label><input type="text" name="sc-titre" [(ngModel)]="scenario.titre" /></div>
              <div class="champ"><label>Durée totale (s)</label><input type="number" name="sc-duree" [(ngModel)]="scenario.dureeTotaleS" /></div>
            </div>
            <div class="champ"><label>Accroche (2 premières secondes)</label><input type="text" name="sc-accroche" [(ngModel)]="scenario.accroche" /></div>
            @for (plan of scenario.plans; track $index) {
              <div class="plan">
                <div class="ligne"><strong>Plan {{ plan.numero }}</strong>
                  <input type="number" [name]="'p-duree-' + $index" [(ngModel)]="plan.dureeS" style="width:80px" /> <span class="aide">s</span>
                  <span class="espace"></span>
                  <button type="button" class="bouton danger petit" (click)="retirerPlan($index)">Retirer</button></div>
                <div class="ligne-champs">
                  <div class="champ"><label>Image</label><textarea rows="2" [name]="'p-img-' + $index" [(ngModel)]="plan.image"></textarea></div>
                  <div class="champ"><label>Texte à l'écran</label><textarea rows="2" [name]="'p-txt-' + $index" [(ngModel)]="plan.texteEcran"></textarea></div>
                </div>
                <div class="ligne-champs">
                  <div class="champ"><label>Voix off</label><textarea rows="2" [name]="'p-voix-' + $index" [(ngModel)]="plan.voixOff"></textarea></div>
                  <div class="champ"><label>Son</label><input type="text" [name]="'p-son-' + $index" [(ngModel)]="plan.son" /></div>
                </div>
              </div>
            }
            <button type="button" class="bouton secondaire petit" (click)="ajouterPlan()">+ Ajouter un plan</button>
            <div class="champ" style="margin-top:14px"><label>Matériel</label><app-puces [(valeurs)]="materiel" /></div>
          } @else {
            <h2>Brief visuel</h2>
            <div class="champ"><label>Pour le graphiste</label><textarea name="b-desc" rows="2" [(ngModel)]="brief.description"></textarea></div>
            <div class="champ">
              <div class="ligne"><label class="espace">Prompt d'image <span class="aide">à coller dans Canva, Ideogram, Midjourney…</span></label>
                <button type="button" class="bouton secondaire petit" (click)="copier(brief.promptImage || '', 'prompt')">{{ copie() === 'prompt' ? 'Copié' : 'Copier le prompt' }}</button></div>
              <textarea name="b-prompt" rows="4" class="mono" [(ngModel)]="brief.promptImage"></textarea>
            </div>
            <div class="champ"><label>Format d'image</label>
              <select name="b-ratio" [(ngModel)]="brief.ratio" style="max-width:200px">
                <option value="1:1">Carré 1:1</option><option value="4:5">Portrait 4:5</option><option value="9:16">Vertical 9:16</option>
              </select></div>
            <div class="images">
              @if (moteurs().length > 1) {
                <div class="champ"><label>Moteur d'images</label>
                  <select name="moteur" [(ngModel)]="moteur" style="max-width:360px">
                    @for (m of moteurs(); track m) { <option [value]="m">{{ libelleMoteur(m) }}</option> }
                  </select></div>
              }
              @if (habiller) {
                <div class="champ"><label>Photo de fond</label>
                  <select name="b-photo" [(ngModel)]="sourcePhoto" style="max-width:360px">
                    <option value="AUTO">Automatique : banque du client, sinon IA</option>
                    <option value="BANQUE">Banque du client uniquement</option>
                    <option value="IA">Générée par l'IA uniquement</option>
                  </select></div>
              }
              <label class="case-habiller"><input type="checkbox" name="habiller" [(ngModel)]="habiller" />
                Affiche complète : l'IA fournit la photo, OkPost écrit le titre et ajoute logo et coordonnées</label>
              @if (habiller) {
                <div class="champ"><label>Mise en page</label>
                  <select name="b-gabarit" [(ngModel)]="brief.gabarit" style="max-width:360px">
                    <option value="">Automatique</option>
                    <option value="CHIFFRE">Gros chiffre</option>
                    <option value="TITRE_HAUT">Titre en haut</option>
                    <option value="DIAGONALE">Panneau diagonal</option>
                    <option value="PLEIN_CADRE">Photo pleine page</option>
                  </select></div>
                <div class="champ"><label>Titre <span class="aide">2 à 7 mots, écrits sur l'affiche</span></label>
                  <input type="text" name="b-accroche" [(ngModel)]="brief.accroche" maxlength="120" /></div>
                @if (brief.gabarit === 'CHIFFRE' || brief.chiffre) {
                  <div class="champ"><label>Chiffre <span class="aide">ex. 850, 0 F, -30 %</span></label>
                    <input type="text" name="b-chiffre" [(ngModel)]="brief.chiffre" maxlength="12" style="max-width:200px" /></div>
                }
                <div class="champ"><label>Texte d'appui <span class="aide">une phrase, facultatif</span></label>
                  <textarea name="b-texte" rows="2" [(ngModel)]="brief.texte" maxlength="300"></textarea></div>
                <div class="champ"><label>Bouton <span class="aide">2 à 5 mots, facultatif</span></label>
                  <input type="text" name="b-cta" [(ngModel)]="brief.cta" maxlength="60" style="max-width:360px" /></div>
              }
              <div class="ligne">
                <button type="button" class="bouton" [disabled]="generationImage() || !brief.promptImage" (click)="genererImage()">
                  @if (generationImage()) { Génération de l'image… (20 à 40 s) }
                  @else if (pub.visuels?.length) { Générer une autre image } @else { Générer l'image }
                </button>
                <span class="aide">À partir du prompt ci-dessus, au format choisi. Payant chez le fournisseur ; quota mensuel selon la formule.</span>
              </div>
            </div>
            @if (pub.format === 'CARROUSEL') {
              <h3>Diapositives du carrousel</h3>
              <p class="aide">Chaque diapositive a son visuel : son titre est écrit sur l’image, son texte guide la scène.
                Même prompt de base pour toutes, pour garder un style cohérent.</p>
              @for (t of brief.textesCarrousel; track $index) {
                <div class="plan diapo">
                  <div class="vignette">
                    @if (imageDiapo($index + 1); as v) {
                      <a [href]="v.url" target="_blank" rel="noopener"><img [src]="v.url" alt="" loading="lazy" /></a>
                    } @else { <span>{{ $index + 1 }}</span> }
                  </div>
                  <div class="espace">
                    <div class="ligne"><strong>Diapositive {{ $index + 1 }}</strong><span class="espace"></span>
                      <button type="button" class="bouton danger petit" (click)="brief.textesCarrousel!.splice($index, 1)">Retirer</button></div>
                    <div class="champ"><label>Titre <span class="aide">écrit sur le visuel</span></label><input type="text" [name]="'c-titre-' + $index" [(ngModel)]="t.titre" /></div>
                    <div class="champ"><label>Texte</label><textarea rows="2" [name]="'c-texte-' + $index" [(ngModel)]="t.texte"></textarea></div>
                    <div class="ligne">
                      <button type="button" class="bouton secondaire petit" [disabled]="diapoEnCours() !== null || !brief.promptImage"
                        (click)="genererDiapo($index + 1)">
                        @if (diapoEnCours() === $index + 1) { Génération… } @else if (imageDiapo($index + 1)) { Régénérer le visuel } @else { Générer le visuel }
                      </button>
                      <label class="bouton secondaire petit" style="margin:0">Téléverser
                        <input type="file" hidden accept="image/jpeg,image/png,image/webp" (change)="televerserDiapo($event, $index + 1)" /></label>
                    </div>
                  </div>
                </div>
              }
              <div class="ligne">
                <button type="button" class="bouton secondaire petit" (click)="ajouterVisuel()">+ Ajouter une diapositive</button>
                @if (brief.textesCarrousel!.length > 1) {
                  <button type="button" class="bouton petit" [disabled]="diapoEnCours() !== null || !brief.promptImage" (click)="genererToutesDiapos()">
                    {{ manquantes() ? 'Générer les ' + manquantes() + ' visuel(s) manquant(s)' : 'Tous les visuels sont prêts' }}</button>
                }
              </div>

              <div class="montage">
                <h3>Sur Facebook</h3>
                <p class="aide">Facebook ne propose pas de carrousel pour les publications de Page. Choisis comment le publier.</p>
                <div class="modes">
                  <label class="mode" [class.actif]="modeCarrousel() === 'VIDEO'">
                    <input type="radio" name="mode-carrousel" value="VIDEO" [ngModel]="modeCarrousel()" (ngModelChange)="choisirMode($event)" />
                    <span><strong>Vidéo défilante</strong><br /><span class="aide">Les visuels s’enchaînent en Reel vertical, avec musique si tu veux.</span></span>
                  </label>
                  <label class="mode" [class.actif]="modeCarrousel() === 'PHOTOS'">
                    <input type="radio" name="mode-carrousel" value="PHOTOS" [ngModel]="modeCarrousel()" (ngModelChange)="choisirMode($event)" />
                    <span><strong>Plusieurs photos</strong><br /><span class="aide">Les visuels dans une seule publication, en mosaïque.</span></span>
                  </label>
                </div>
                @if (modeCarrousel() === 'VIDEO') {
                  <div class="ligne-champs">
                    <div class="champ"><label>Durée par visuel</label>
                      <select name="m-secondes" [(ngModel)]="secondesMontage">
                        @for (n of [2,3,4,5,6]; track n) { <option [ngValue]="n">{{ n }} secondes</option> }
                      </select></div>
                    <div class="champ"><label>Transition</label>
                      <select name="m-transition" [(ngModel)]="transitionMontage">
                        <option value="fondu">Fondu</option><option value="glissement">Glissement</option><option value="zoom">Zoom</option>
                      </select></div>
                    <div class="champ"><label>Musique <span class="aide">facultative, MP3 ou M4A, 15 Mo</span></label>
                      <input type="file" accept="audio/mpeg,audio/mp4,audio/x-m4a,audio/aac,audio/wav,audio/ogg" (change)="choisirMusique($event)" /></div>
                  </div>
                  <p class="aide">Utilise une musique libre de droits ou dont le client a les droits : Facebook peut couper le son d’un Reel sinon.</p>
                  <button type="button" class="bouton" [disabled]="montageEnCours() || diapositivesPretes() < 2" (click)="monter()">
                    @if (montageEnCours()) { Montage en cours… (10 à 30 s) } @else if (montageActuel()) { Refaire la vidéo } @else { Créer la vidéo ({{ diapositivesPretes() }} visuels) }
                  </button>
                  @if (montageActuel(); as m) {
                    <div class="apercu-video"><video [src]="m.url" controls preload="metadata"></video>
                      <span class="aide">{{ m.prompt }}</span></div>
                  }
                } @else if (modeCarrousel() === 'PHOTOS') {
                  <p class="aide">{{ diapositivesPretes() }} visuel(s) prêt(s) seront publiés ensemble, dans l’ordre des diapositives.</p>
                }
              </div>
            }
          }

          <h2>Visuels et vidéos</h2>
          <div class="medias">
            <div class="ligne">
              <label class="bouton secondaire" style="margin:0">
                {{ envoiMedia() ? 'Envoi… ' + progression() + ' %' : 'Téléverser une photo ou une vidéo' }}
                <input type="file" hidden accept="image/jpeg,image/png,image/webp,video/mp4,video/quicktime,video/webm"
                       [disabled]="envoiMedia()" (change)="televerser($event)" />
              </label>
              <span class="aide">Photo JPG, PNG ou WebP (10 Mo) ; vidéo MP4, MOV ou WebM (200 Mo). Pas besoin de générer.</span>
            </div>
            @if (envoiMedia()) { <div class="barre" style="margin-top:8px"><div [style.width.%]="progression()"></div></div> }
            @if (pub.visuels?.length) {
              <div class="galerie">
                @for (v of pub.visuels; track v.id) {
                  <figure>
                    @if (v.type === 'VIDEO') {
                      <video [src]="v.url" controls preload="metadata"></video>
                    } @else {
                      <a [href]="v.url" target="_blank" rel="noopener"><img [src]="v.url" [alt]="v.prompt || v.nomOrigine || 'Visuel'" loading="lazy" /></a>
                    }
                    <figcaption>
                      <span class="pastille">{{ v.source === 'MONTAGE' ? 'Vidéo défilante' : v.source === 'BANQUE' ? 'Banque' : v.source === 'TELEVERSE' ? (v.type === 'VIDEO' ? 'Vidéo' : 'Photo') : 'Générée' }}</span>
                      @if (v.diapositive) { <span class="pastille">Diapo {{ v.diapositive }}</span> }
                      <span class="aide">{{ v.date | date: 'd MMM, HH:mm' }}</span>
                      <span class="espace"></span>
                      <a class="bouton secondaire petit" [href]="v.url" [attr.download]="v.nomOrigine || ('okpost-' + v.id)">Télécharger</a>
                      <button type="button" class="bouton danger petit" (click)="supprimerImage(v.id)">Supprimer</button>
                    </figcaption>
                  </figure>
                }
              </div>
              <p class="aide">Le plus récent est celui que le client verra dans le portail de validation et dans l'export.</p>
            }
          </div>

          <div class="actions">
            <span class="espace"></span>
            <button class="bouton" [disabled]="envoi()">{{ envoi() ? 'Enregistrement…' : 'Enregistrer' }}</button>
          </div>
        </form>

        <div class="carte">
          <h2>Régénérer avec l'IA</h2>
          <div class="champ"><label>Consigne <span class="aide">facultative</span></label>
            <input type="text" [(ngModel)]="consigne" placeholder="Ex. plus court, ton plus festif, mettre en avant la livraison gratuite" /></div>
          <div class="ligne">
            <button class="bouton secondaire" [disabled]="regen()" (click)="regenerer('legende')">La légende</button>
            <button class="bouton secondaire" [disabled]="regen()" (click)="regenerer('cta')">L'appel à l'action</button>
            <button class="bouton secondaire" [disabled]="regen()" (click)="regenerer('tout')">Toute la publication</button>
            @if (regen()) { <span class="aide">Génération…</span> }
          </div>
          <p class="aide" style="margin-top:8px">Les modifications non enregistrées seront remplacées.</p>
        </div>
      } @else if (!erreur()) {
        <p class="chargement">Chargement…</p>
      }
    </div>
  `,
  styles: `
    .retours { border-left: 4px solid var(--orange); }
    .publication-auto { border-left: 4px solid #1877f2; margin-bottom: 16px; }
    .retour { padding: 8px 0; border-top: 1px solid var(--gris-clair); }
    .retour:first-of-type { border-top: none; }
    .retour p { margin: 6px 0 0; }
    .plan { border: 1px solid var(--gris-clair); border-radius: 10px; padding: 12px; margin-bottom: 10px; }
    .actions { display: flex; border-top: 1px solid var(--gris-clair); margin-top: 20px; padding-top: 16px; }
    .images { background: #f9fafb; border-radius: 10px; padding: 14px; margin-bottom: 16px; }
    .case-habiller { display: flex; gap: 8px; align-items: center; font-weight: 500; margin-bottom: 12px; }
    .galerie { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 12px; margin-top: 12px; }
    .galerie figure { margin: 0; background: #fff; border: 1px solid var(--gris-clair); border-radius: 10px; overflow: hidden; }
    .galerie img, .galerie video { width: 100%; display: block; aspect-ratio: 1; object-fit: cover; background: #111; }
    .medias { margin-bottom: 8px; }
    .galerie figcaption { display: flex; gap: 6px; align-items: center; padding: 8px; flex-wrap: wrap; }
    h2 { margin-top: 20px; }
    .diapo { display: flex; gap: 14px; align-items: flex-start; }
    .vignette { width: 110px; flex: none; aspect-ratio: 4/5; border-radius: 8px; background: #eef0f2; overflow: hidden;
      display: flex; align-items: center; justify-content: center; font-size: 1.6rem; font-weight: 700; color: var(--gris); }
    .vignette img { width: 100%; height: 100%; object-fit: cover; display: block; }
    .montage { background: #f4f8ff; border-radius: 10px; padding: 14px; margin: 16px 0; }
    .montage h3 { margin-top: 0; }
    .modes { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-bottom: 12px; }
    .mode { display: flex; gap: 10px; align-items: flex-start; background: #fff; border: 2px solid var(--gris-clair);
      border-radius: 10px; padding: 10px 12px; cursor: pointer; font-weight: 400; margin: 0; }
    .mode.actif { border-color: #1877f2; }
    .mode input { width: auto; margin-top: 3px; }
    .apercu-video { display: flex; gap: 12px; align-items: flex-end; margin-top: 12px; }
    .apercu-video video { width: 180px; aspect-ratio: 9/16; background: #111; border-radius: 8px; }
    @media (max-width: 640px) { .modes { grid-template-columns: 1fr; } .vignette { width: 76px; } }
    .carte > h2:first-child { margin-top: 0; }
  `,
})
export class PublicationComponent implements OnInit {
  private api = inject(ApiService);
  id = input.required<string>();

  p = signal<Publication | null>(null);
  envoi = signal(false);
  regen = signal(false);
  generationImage = signal(false);
  actionAuto = signal(false);
  quand = '';
  envoiMedia = signal(false);
  progression = signal(0);
  habiller = true;
  sourcePhoto = 'AUTO';
  moteurs = signal<string[]>([]);
  moteur = '';
  erreur = signal('');
  message = signal('');
  copie = signal('');
  consigne = '';

  diapoEnCours = signal<number | null>(null);
  montageEnCours = signal(false);
  secondesMontage = 3;
  transitionMontage = 'fondu';
  musique: File | null = null;

  hashtags: string[] = [];
  materiel: string[] = [];
  brief: BriefVisuel = {};
  scenario: ScenarioVideo = { plans: [] };

  reseaux = RESEAUX;
  formats = Object.keys(LIBELLES_FORMAT);
  statuts = Object.keys(LIBELLES_STATUT) as StatutPublication[];
  libellesReseau = LIBELLES_RESEAU;
  libellesFormat = LIBELLES_FORMAT;
  libellesStatut = LIBELLES_STATUT;
  decisions = DECISIONS;

  ngOnInit() {
    this.api.etatIa().subscribe({
      next: e => {
        const dispo = Object.entries(e.images ?? {}).filter(([, ok]) => ok).map(([nom]) => nom);
        this.moteurs.set(dispo);
        this.moteur = e.imageParDefaut && dispo.includes(e.imageParDefaut) ? e.imageParDefaut : (dispo[0] ?? '');
      },
      error: () => {},
    });
    this.api.publication(this.id()).subscribe({ next: p => this.afficher(p), error: e => this.erreur.set(messageErreur(e)) });
  }

  private afficher(p: Publication) {
    this.p.set({ ...p });
    this.hashtags = [...(p.hashtags ?? [])];
    this.brief = structuredClone(p.brief ?? { textesCarrousel: [] });
    this.brief.textesCarrousel ??= [];
    this.scenario = structuredClone(p.scenario ?? { plans: [] });
    this.scenario.plans ??= [];
    this.materiel = [...(this.scenario.materiel ?? [])];
  }

  texteAPublier(p: Publication) {
    return [p.legende ?? '', p.cta ?? '', this.hashtags.join(' ')].filter(x => x.trim()).join('\n\n');
  }

  ajouterPlan() {
    const n = (this.scenario.plans?.length ?? 0) + 1;
    this.scenario.plans!.push({ numero: n, dureeS: 3 });
  }

  retirerPlan(i: number) {
    this.scenario.plans!.splice(i, 1);
    this.scenario.plans!.forEach((x, k) => (x.numero = k + 1));
  }

  ajouterVisuel() {
    const n = this.brief.textesCarrousel!.length + 1;
    this.brief.textesCarrousel!.push({ visuel: n, titre: '', texte: '' });
  }

  enregistrer() {
    const p = this.p();
    if (!p) return;
    this.envoi.set(true);
    this.erreur.set('');
    const patch: Partial<Publication> = {
      date: p.date, reseau: p.reseau, format: p.format, statut: p.statut, idee: p.idee, legende: p.legende ?? '',
      cta: p.cta ?? '', hashtags: this.hashtags, version: p.version,
    };
    if (p.format === 'VIDEO') {
      patch.scenario = { ...this.scenario, materiel: this.materiel };
    } else {
      this.brief.textesCarrousel?.forEach((t, k) => (t.visuel = k + 1));
      patch.brief = this.brief;
    }
    this.api.modifierPublication(p.id, patch).subscribe({
      next: maj => {
        this.afficher(maj);
        this.envoi.set(false);
        this.message.set('Publication enregistrée.');
        setTimeout(() => this.message.set(''), 2500);
      },
      error: e => { this.erreur.set(messageErreur(e)); this.envoi.set(false); },
    });
  }

  libelleMoteur(m: string) {
    return m === 'gemini' ? 'Gemini Nano Banana (payant, meilleur texte)'
      : m === 'cloudflare' ? 'FLUX schnell via Cloudflare (gratuit, texte moins fiable)' : m;
  }

  genererImage() {
    const p = this.p();
    if (!p) return;
    this.generationImage.set(true);
    this.erreur.set('');
    this.api.genererImage(p.id, this.brief.promptImage, this.brief.ratio, this.habiller, this.brief.accroche ?? '', this.moteur,
      undefined, { gabarit: this.brief.gabarit, chiffre: this.brief.chiffre, texte: this.brief.texte, cta: this.brief.cta, photo: this.sourcePhoto }).subscribe({
      next: v => {
        this.p.set({ ...p, visuels: [v, ...(p.visuels ?? [])] });
        this.generationImage.set(false);
      },
      error: e => { this.erreur.set(messageErreur(e)); this.generationImage.set(false); window.scrollTo({ top: 0 }); },
    });
  }

  // ---------- Carrousel ----------

  imageDiapo(n: number) {
    return this.p()?.visuels?.find(v => v.diapositive === n && v.type !== 'VIDEO') ?? null;
  }

  diapositivesPretes() {
    const n = this.brief.textesCarrousel?.length ?? 0;
    let pretes = 0;
    for (let i = 1; i <= n; i++) if (this.imageDiapo(i)) pretes++;
    return pretes;
  }

  manquantes() {
    return (this.brief.textesCarrousel?.length ?? 0) - this.diapositivesPretes();
  }

  modeCarrousel() {
    const p = this.p();
    return p?.modeCarrousel ?? (this.montageActuel() ? 'VIDEO' : 'PHOTOS');
  }

  montageActuel() {
    return this.p()?.visuels?.find(v => v.source === 'MONTAGE') ?? null;
  }

  /** Les textes des diapositives doivent être enregistrés : le serveur s'en sert pour le prompt. */
  private enregistrerPuis(suite: () => void) {
    const p = this.p();
    if (!p) return;
    this.brief.textesCarrousel?.forEach((t, k) => (t.visuel = k + 1));
    this.api.modifierPublication(p.id, { brief: this.brief, version: p.version }).subscribe({
      next: maj => { const visuels = this.p()?.visuels; this.afficher({ ...maj, visuels: maj.visuels ?? visuels }); suite(); },
      error: e => { this.erreur.set(messageErreur(e)); this.diapoEnCours.set(null); window.scrollTo({ top: 0 }); },
    });
  }

  genererDiapo(n: number, ensuite?: () => void) {
    this.diapoEnCours.set(n);
    this.erreur.set('');
    this.enregistrerPuis(() => this.genererDiapoEnregistree(n, ensuite));
  }

  private genererDiapoEnregistree(n: number, ensuite?: () => void) {
    const p = this.p();
    if (!p) return;
    this.diapoEnCours.set(n);
    this.api.genererImage(p.id, this.brief.promptImage, this.brief.ratio, this.habiller, '', this.moteur, n).subscribe({
      next: v => {
        const actuel = this.p()!;
        this.p.set({ ...actuel, visuels: [v, ...(actuel.visuels ?? [])] });
        this.diapoEnCours.set(null);
        ensuite?.();
      },
      error: e => { this.erreur.set(`Diapositive ${n} : ${messageErreur(e)}`); this.diapoEnCours.set(null); window.scrollTo({ top: 0 }); },
    });
  }

  /** Génère les visuels manquants l'un après l'autre (les fournisseurs limitent les appels simultanés). */
  genererToutesDiapos() {
    const total = this.brief.textesCarrousel?.length ?? 0;
    const restantes: number[] = [];
    for (let i = 1; i <= total; i++) if (!this.imageDiapo(i)) restantes.push(i);
    if (!restantes.length) return;
    this.diapoEnCours.set(restantes[0]);
    this.erreur.set('');
    const suivante = () => {
      const n = restantes.shift();
      if (n !== undefined) this.genererDiapoEnregistree(n, suivante);
    };
    this.enregistrerPuis(suivante);
  }

  televerserDiapo(ev: Event, n: number) {
    const champ = ev.target as HTMLInputElement;
    const fichier = champ.files?.[0];
    const p = this.p();
    if (!fichier || !p) return;
    this.diapoEnCours.set(n);
    this.api.televerserMedia(p.id, fichier, n).subscribe({
      next: e => {
        if (e.type === HttpEventType.Response && e.body) {
          const actuel = this.p()!;
          this.p.set({ ...actuel, visuels: [e.body, ...(actuel.visuels ?? [])] });
          this.diapoEnCours.set(null);
          champ.value = '';
        }
      },
      error: err => { this.erreur.set(messageErreur(err)); this.diapoEnCours.set(null); champ.value = ''; },
    });
  }

  choisirMode(mode: 'VIDEO' | 'PHOTOS') {
    const p = this.p();
    if (!p) return;
    this.p.set({ ...p, modeCarrousel: mode });
    this.api.modeCarrousel(p.id, mode).subscribe({
      next: maj => { const actuel = this.p()!; this.p.set({ ...actuel, modeCarrousel: maj.modeCarrousel, version: maj.version }); },
      error: e => this.erreur.set(messageErreur(e)),
    });
  }

  choisirMusique(ev: Event) {
    const f = (ev.target as HTMLInputElement).files?.[0] ?? null;
    if (f && f.size > 15 * 1024 * 1024) { this.erreur.set('Musique trop lourde (15 Mo maximum)'); return; }
    this.musique = f;
  }

  monter() {
    const p = this.p();
    if (!p) return;
    this.montageEnCours.set(true);
    this.erreur.set('');
    this.api.monterCarrousel(p.id, this.secondesMontage, this.transitionMontage, this.musique).subscribe({
      next: v => {
        const actuel = this.p()!;
        this.p.set({ ...actuel, visuels: [v, ...(actuel.visuels ?? [])], modeCarrousel: 'VIDEO' });
        this.montageEnCours.set(false);
        if (actuel.modeCarrousel !== 'VIDEO') this.choisirMode('VIDEO');
      },
      error: e => { this.erreur.set(messageErreur(e)); this.montageEnCours.set(false); window.scrollTo({ top: 0 }); },
    });
  }

  programmer() {
    const p = this.p();
    if (!p) return;
    this.lancer(this.api.programmer(p.id, this.quand ? new Date(this.quand).toISOString() : undefined));
  }

  annulerProgrammation() {
    const p = this.p();
    if (p) this.lancer(this.api.annulerProgrammation(p.id));
  }

  publierMaintenant() {
    const p = this.p();
    if (!p || !confirm(p.reseau === 'INSTAGRAM' ? 'Publier maintenant sur le compte Instagram du client ?' : 'Publier maintenant sur la Page Facebook du client ?')) return;
    this.lancer(this.api.publierMaintenant(p.id));
  }

  private lancer(appel: import('rxjs').Observable<Publication>) {
    this.actionAuto.set(true);
    this.erreur.set('');
    appel.subscribe({
      next: maj => { this.afficher(maj); this.actionAuto.set(false); },
      error: e => { this.erreur.set(messageErreur(e)); this.actionAuto.set(false); window.scrollTo({ top: 0 }); },
    });
  }

  televerser(ev: Event) {
    const champ = ev.target as HTMLInputElement;
    const fichier = champ.files?.[0];
    const p = this.p();
    if (!fichier || !p) return;
    const video = fichier.type.startsWith('video/');
    if (fichier.size > (video ? 200 : 10) * 1024 * 1024) {
      this.erreur.set(video ? 'Vidéo trop lourde (200 Mo maximum)' : 'Photo trop lourde (10 Mo maximum)');
      champ.value = '';
      return;
    }
    this.envoiMedia.set(true);
    this.progression.set(0);
    this.erreur.set('');
    this.api.televerserMedia(p.id, fichier).subscribe({
      next: e => {
        if (e.type === HttpEventType.UploadProgress && e.total) {
          this.progression.set(Math.round((e.loaded / e.total) * 100));
        } else if (e.type === HttpEventType.Response && e.body) {
          const actuel = this.p();
          if (actuel) this.p.set({ ...actuel, visuels: [e.body, ...(actuel.visuels ?? [])] });
          this.envoiMedia.set(false);
          champ.value = '';
        }
      },
      error: err => { this.erreur.set(messageErreur(err)); this.envoiMedia.set(false); champ.value = ''; window.scrollTo({ top: 0 }); },
    });
  }

  supprimerImage(visuelId: string) {
    const p = this.p();
    if (!p || !confirm('Supprimer cette image ?')) return;
    this.api.supprimerImage(p.id, visuelId).subscribe({
      next: () => this.p.set({ ...p, visuels: (p.visuels ?? []).filter(v => v.id !== visuelId) }),
      error: e => this.erreur.set(messageErreur(e)),
    });
  }

  regenerer(champ: 'legende' | 'cta' | 'tout') {
    const p = this.p();
    if (!p) return;
    this.regen.set(true);
    this.erreur.set('');
    this.api.regenerer(p.id, champ, this.consigne).subscribe({
      next: maj => { this.afficher(maj); this.regen.set(false); },
      error: e => { this.erreur.set(messageErreur(e)); this.regen.set(false); },
    });
  }

  copier(texte: string, cle: string) {
    navigator.clipboard?.writeText(texte).then(() => {
      this.copie.set(cle);
      setTimeout(() => this.copie.set(''), 2000);
    });
  }
}
