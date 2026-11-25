import { Component, computed, inject, input, signal, OnInit } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService, messageErreur } from '../core/api.service';
import { ConfirmationService } from '../core/confirmation.service';
import { Asset, Client, FicheProposee, Offre, RESEAUX, LIBELLES_RESEAU } from '../core/models';
import { PucesComponent } from '../shared/puces.component';
import { BanqueImagesComponent } from '../shared/banque-images.component';
import { ContactsValidationComponent } from '../shared/contacts-validation.component';
import { AssistantRedactionComponent } from '../shared/assistant-redaction.component';
import { ReformulerToutComponent } from '../shared/reformuler-tout.component';
import { ImportCatalogueWebComponent } from '../shared/import-catalogue-web.component';

type Onglet = 'identite' | 'offres' | 'localisation' | 'audience' | 'ton' | 'visuel' | 'en-ligne';

const TYPES_FICHIERS = [
  { valeur: 'LOGO_COULEUR', libelle: 'Logo couleur' },
  { valeur: 'LOGO_BLANC', libelle: 'Logo blanc' },
  { valeur: 'LOGO_NOIR', libelle: 'Logo noir' },
  { valeur: 'ICONE', libelle: 'Icône' },
  { valeur: 'PHOTO', libelle: 'Photo' },
];

@Component({
  selector: 'app-fiche-client',
  imports: [FormsModule, RouterLink, PucesComponent, BanqueImagesComponent, ContactsValidationComponent, AssistantRedactionComponent, ReformulerToutComponent, ImportCatalogueWebComponent],
  template: `
    <div class="page page-etroite">
      <div class="entete-page">
        <div>
          <h1>{{ id() ? c.nom || 'Fiche client' : 'Nouveau client' }}</h1>
          <p class="sous-titre">Plus la fiche est précise, plus les contenus générés sonnent juste.</p>
        </div>
        @if (id()) { <a class="bouton secondaire" [routerLink]="['/clients', id()]">Retour</a> }
      </div>

      @if (erreur()) { <div class="erreur">{{ erreur() }}</div> }
      @if (message()) { <div class="succes">{{ message() }}</div> }

      <section class="carte assistant-site" aria-label="Remplir la fiche depuis le site web">
        <h2>Gagne du temps : remplis la fiche depuis le site</h2>
        <p class="aide">Colle l'adresse du site du client : OkPost lit ses pages et propose une fiche (description, offres, cibles, ton, couleurs, réseaux, coordonnées…).
          Tu relis et corriges avant d'enregistrer.</p>
        <div class="ligne">
          <input type="text" class="espace" name="site-analyse" placeholder="exemple.com" aria-label="Adresse du site web du client"
                 [ngModel]="siteUrl || c.domaine || ''" (ngModelChange)="siteUrl = $event" (keydown.enter)="$event.preventDefault(); lireSite()" />
          <button type="button" class="bouton" [disabled]="lectureSite() || !(siteUrl || c.domaine)" (click)="lireSite()">
            {{ lectureSite() ? 'Lecture du site… (30 s au plus)' : 'Lire le site' }}
          </button>
          @if (id()) {
            <button type="button" class="bouton secondaire" [disabled]="lectureSite() || !(siteUrl || c.domaine)" (click)="chercherImages()">Chercher les images</button>
          }
        </div>
        @if (id()) {
          <p class="aide" style="margin-top:10px"><strong>Pas de site web ?</strong> Si la Page Facebook ou le compte Instagram du client est déjà relié (onglet Réseaux),
            OkPost peut lire son profil (à propos, adresse, horaires, téléphone, publications) pour proposer la fiche.</p>
          <button type="button" class="bouton secondaire" [disabled]="lectureSite()" (click)="lireReseaux()">
            {{ lectureSite() ? 'Lecture en cours…' : 'Lire les pages Facebook / Instagram' }}
          </button>
        }
        <label class="case" style="margin-top:8px"><input type="checkbox" name="ecraser" [(ngModel)]="ecraser" /> Remplacer aussi les champs déjà remplis</label>
        @if (resumeSite(); as r) {
          <div class="succes" style="margin-top:12px">
            @if (r.remplis.length) {
              <strong>{{ r.remplis.length }} champ{{ r.remplis.length > 1 ? 's' : '' }} rempli{{ r.remplis.length > 1 ? 's' : '' }}</strong> : {{ r.remplis.join(', ') }}.
              Parcours les onglets pour vérifier, puis enregistre.
            } @else { Rien de nouveau à ajouter : les champs trouvés sont déjà remplis. }
            @if (r.ignores.length) { <br /><span class="aide">Déjà remplis, non modifiés : {{ r.ignores.join(', ') }}.</span> }
          </div>
          @for (a of r.avertissements; track a) { <div class="info" style="margin-top:8px">{{ a }}</div> }
        }

        @if (logosTrouves().length || photosTrouvees().length) {
          <div class="images-site">
            <h3>Images trouvées</h3>
            @if (!id()) {
              <div class="info">Enregistre d'abord la fiche : tu pourras ensuite importer le logo et les photos (bouton « Chercher les images »).</div>
            }
            @if (logosTrouves().length) {
              <p class="aide"><strong>Logo</strong> : choisis la bonne version.</p>
              <div class="vignettes">
                @for (u of logosTrouves(); track u) {
                  <label class="vignette">
                    <input type="radio" name="logo-site" [checked]="logoChoisi() === u" (change)="logoChoisi.set(u)" aria-label="Utiliser cette image comme logo" />
                    <img [src]="u" alt="Logo possible" referrerpolicy="no-referrer" loading="lazy" (error)="ecarter(u)" />
                  </label>
                }
                <label class="vignette aucun"><input type="radio" name="logo-site" [checked]="!logoChoisi()" (change)="logoChoisi.set(null)" /> Aucun</label>
              </div>
            }
            @if (photosTrouvees().length) {
              <p class="aide" style="margin-top:12px"><strong>Photos</strong> pour la banque d'images : {{ nbPhotosChoisies() }} sélectionnée{{ nbPhotosChoisies() > 1 ? 's' : '' }}.</p>
              <div class="vignettes">
                @for (u of photosTrouvees(); track u; let i = $index) {
                  <label class="vignette">
                    <input type="checkbox" [checked]="photosChoisies().has(u)" (change)="basculerPhoto(u)" [attr.aria-label]="'Importer la photo ' + (i + 1)" />
                    <img [src]="u" alt="Photo du site" referrerpolicy="no-referrer" loading="lazy" (error)="ecarter(u)" />
                  </label>
                }
              </div>
            }
            <p class="aide" style="margin-top:10px">N'importe que des images que le client t'autorise à utiliser (ses propres photos de Page, par exemple). Les photos sont réduites, converties en JPG puis décrites par l'IA.</p>
            <button type="button" class="bouton" [disabled]="!id() || importEnCours() || (!logoChoisi() && !nbPhotosChoisies())" (click)="importerImages()">
              {{ importEnCours() ? 'Import en cours… ' + progression() : 'Importer la sélection' }}
            </button>
            @if (resumeImport()) { <div class="succes" style="margin-top:10px">{{ resumeImport() }}</div> }
          </div>
        }
      </section>

      <div class="onglets" role="tablist">
        @for (o of onglets; track o.id) {
          <button type="button" role="tab" [class.actif]="onglet() === o.id" (click)="onglet.set(o.id)">{{ o.libelle }}</button>
        }
      </div>

      <form class="carte" (ngSubmit)="enregistrer()" (input)="modifie.set(true)">
        @switch (onglet()) {
          @case ('identite') {
            <div class="champ"><label>Nom *</label><input type="text" name="nom" [(ngModel)]="c.nom" required /></div>
            <div class="champ"><label>Type d'activité <span class="aide">adapte les idées, les visuels et les appels à l'action</span></label>
              <select name="type-activite" [(ngModel)]="c.typeActivite" style="max-width:420px">
                <option value="SERVICES">Prestations de services</option>
                <option value="PRODUITS">Vente de produits (boutique)</option>
                <option value="MIXTE">Produits et services</option>
              </select></div>
            <div class="ligne-champs">
              <div class="champ"><label>Secteur</label><input type="text" name="secteur" [(ngModel)]="c.secteur" placeholder="Agroalimentaire, santé, transport…" /></div>
              <div class="champ"><label>Année de création</label><input type="number" name="annee" [(ngModel)]="c.anneeCreation" /></div>
            </div>
            <div class="champ"><label>Description <span class="aide">ce que fait l'entreprise, pour qui, ce qui la rend différente</span></label>
              <textarea name="description" rows="5" [(ngModel)]="c.description"></textarea>
              <app-assistant-redaction [texte]="c.description ?? ''" [sujet]="c.nom" champ="activite" [clientId]="id()" (appliquer)="c.description = $event; modifie.set(true)" /></div>
            <div class="champ"><label>Slogan</label><input type="text" name="slogan" [(ngModel)]="c.slogan" /></div>
          }
          @case ('offres') {
            <p class="aide">Produits ou services. Les prix indiqués ici sont les seuls que l'IA pourra citer.
              @if (c.typeActivite !== 'SERVICES') { Pour chaque produit, rattache ses vraies photos : les publications montreront le vrai article, jamais une image inventée par l'IA. }</p>
            @if (c.typeActivite !== 'SERVICES') {
              <app-import-catalogue-web [clientId]="id()" [siteUrl]="siteUrl || c.domaine || ''" [offres]="c.offres"
                                        (modifie)="modifie.set(true)" (assetAjoute)="ajouterAsset($event)" />
              <div class="import-catalogue">
                <strong>Importer un catalogue</strong>
                <p class="aide">Depuis Excel ou Google Sheets : enregistre ton tableau en CSV (colonnes : nom, prix, description, categorie, tailles, couleurs, stock, photo).
                  Pour rattacher les photos, ajoute-les d'abord à la banque d'images puis écris leur nom de fichier dans la colonne photo.
                  Les produits importés s'ajoutent à la liste (un produit du même nom est mis à jour) : relis, puis enregistre la fiche.</p>
                <div class="ligne" style="gap:10px;flex-wrap:wrap">
                  <input type="file" accept=".csv,text/csv,text/plain" (change)="importerCatalogue($event)" [disabled]="!id() || importCatalogue()" aria-label="Fichier CSV du catalogue" />
                  <button type="button" class="bouton secondaire petit" (click)="telechargerModele()">Télécharger un modèle</button>
                </div>
                @if (!id()) { <p class="aide">Enregistre d'abord la fiche pour pouvoir importer.</p> }
                @if (importCatalogue()) { <p class="aide">Lecture du fichier…</p> }
                @if (resumeCatalogue(); as r) {
                  <div [class]="r.erreur ? 'erreur' : 'succes'" role="status" style="margin-top:8px">{{ r.texte }}
                    @for (w of r.avertissements; track $index) { <br />· {{ w }} }
                  </div>
                }
              </div>
            }
            @if (c.offres.length > 0) {
              <app-reformuler-tout [offres]="c.offres" [clientId]="id()" (modifie)="modifie.set(true)" />
            }
            @if (c.offres.length > 1) {
              <div class="outils-offres">
                <span class="aide">{{ c.offres.length }} élément(s) dans le catalogue@if (c.typeActivite !== 'SERVICES' && nbSansPhoto() > 0) { · <strong>{{ nbSansPhoto() }} sans photo</strong> }</span>
                <span class="espace"></span>
                @if (c.offres.length > 6) {
                  <input type="search" name="filtre-offres" [(ngModel)]="filtreOffres" (input)="$event.stopPropagation()" placeholder="Chercher un produit…" aria-label="Chercher un produit" class="recherche-offres" />
                }
                <button type="button" class="bouton secondaire petit" (click)="toutDeplier()">Tout déplier</button>
                <button type="button" class="bouton secondaire petit" (click)="toutReplier()">Tout replier</button>
              </div>
            }
            @for (o of c.offres; track $index) {
              @if (visibleOffre(o)) {
              <div class="offre" [class.ouverte]="estOuverte(o)">
                <div class="offre-entete">
                  <button type="button" class="bascule" (click)="basculerOffre(o)" [attr.aria-expanded]="estOuverte(o)">
                    <span class="chevron" aria-hidden="true">{{ estOuverte(o) ? '▾' : '▸' }}</span>
                    <strong>{{ o.nom || 'Nouvelle offre' }}</strong>
                    @if (o.prix) { <span class="prix">{{ o.prix }}</span> }
                    @if (o.categorie) { <span class="aide">· {{ o.categorie }}</span> }
                    @if (c.typeActivite !== 'SERVICES') {
                      @if (o.disponible === false) { <span class="pastille statut-A_MODIFIER">Rupture</span> }
                      @if (o.photos?.length) { <span class="aide">· {{ o.photos!.length }} photo{{ o.photos!.length > 1 ? 's' : '' }}</span> }
                      @else { <span class="aide sans-photo">· sans photo</span> }
                    }
                  </button>
                  <button type="button" class="bouton danger petit" (click)="retirerOffre(o)" [attr.aria-label]="'Retirer ' + (o.nom || 'cette offre')">Retirer</button>
                </div>
                @if (estOuverte(o)) {
                <div class="offre-corps">
                <div class="ligne-champs">
                  <div class="champ"><label>Offre</label><input type="text" [name]="'offre-nom-' + $index" [(ngModel)]="o.nom" /></div>
                  <div class="champ"><label>Prix</label><input type="text" [name]="'offre-prix-' + $index" [(ngModel)]="o.prix" placeholder="25 000 FCFA" /></div>
                </div>
                <div class="champ"><label>Description</label><textarea rows="2" [name]="'offre-desc-' + $index" [(ngModel)]="o.description"></textarea>
                  <app-assistant-redaction [texte]="o.description ?? ''" [sujet]="o.nom" champ="produit" [clientId]="id()" [photoId]="o.photos?.[0]" (appliquer)="o.description = $event; modifie.set(true)" /></div>
                @if (c.typeActivite !== 'SERVICES') {
                  <div class="ligne-champs">
                    <div class="champ"><label>Catégorie <span class="aide">facultatif</span></label>
                      <input type="text" [name]="'offre-cat-' + $index" [(ngModel)]="o.categorie" placeholder="Soutiens-gorge" /></div>
                    <div class="champ"><label>Disponibilité</label>
                      <select [name]="'offre-dispo-' + $index" [ngModel]="o.disponible === false ? 'non' : 'oui'" (ngModelChange)="o.disponible = $event === 'non' ? false : undefined">
                        <option value="oui">En stock</option>
                        <option value="non">En rupture</option>
                      </select></div>
                  </div>
                  <div class="ligne-champs">
                    <div class="champ"><label>Tailles <span class="aide">séparées par des virgules</span></label>
                      <input type="text" [name]="'offre-tailles-' + $index" [ngModel]="enTexte(o.tailles)" (change)="definirListe(o, 'tailles', $any($event.target).value)" placeholder="85, 90, 95, 100" /></div>
                    <div class="champ"><label>Couleurs <span class="aide">séparées par des virgules</span></label>
                      <input type="text" [name]="'offre-couleurs-' + $index" [ngModel]="enTexte(o.couleurs)" (change)="definirListe(o, 'couleurs', $any($event.target).value)" placeholder="noir, nude, rouge" /></div>
                  </div>
                  <div class="champ"><label>Autres précisions <span class="aide">facultatif (matière, entretien…)</span></label>
                    <input type="text" [name]="'offre-var-' + $index" [(ngModel)]="o.variantes" /></div>
                  <div class="champ"><label>Photos du produit</label>
                    @if (!id()) {
                      <p class="aide">Enregistre d'abord la fiche et ajoute des photos à la banque d'images (onglet Identité visuelle) pour les rattacher ici.</p>
                    } @else if (!photosBanque().length) {
                      <p class="aide">La banque d'images est vide : ajoute les photos du client (onglet Identité visuelle), puis reviens ici.</p>
                    } @else {
                      <div class="photos-produit">
                        @for (ph of photosDe(o); track ph.id) { <img class="mini" [src]="ph.url" [alt]="ph.legende || ph.description || 'Photo du produit'" loading="lazy" /> }
                        @if (!o.photos?.length) { <span class="aide">Aucune photo choisie.</span> }
                        <button type="button" class="bouton secondaire petit" (click)="photosOuvert.set(photosOuvert() === $index ? null : $index)">
                          {{ photosOuvert() === $index ? 'Fermer' : 'Choisir les photos' }}
                        </button>
                      </div>
                      @if (photosOuvert() === $index) {
                        <div class="vignettes" style="margin-top:8px">
                          @for (a of photosBanque(); track a.id) {
                            <label class="vignette">
                              <input type="checkbox" [checked]="o.photos?.includes(a.id)" (change)="basculerPhotoProduit(o, a.id)" [attr.aria-label]="'Rattacher cette photo à ' + (o.nom || 'ce produit')" />
                              <img [src]="a.url" [alt]="a.legende || a.description || 'Photo'" loading="lazy" />
                            </label>
                          }
                        </div>
                      }
                    }
                  </div>
                }
                <div class="ligne" style="gap:8px;flex-wrap:wrap">
                  @if (c.typeActivite !== 'SERVICES' && id() && o.nom) {
                    <button type="button" class="bouton secondaire petit" (click)="copierLienCommande(o)">{{ lienCopie() === o.nom ? 'Lien copié' : 'Copier le lien de commande WhatsApp' }}</button>
                  }
                </div>
                </div>
                }
              </div>
              }
            }
            <button type="button" class="bouton secondaire" (click)="ajouterOffre()">+ Ajouter une offre</button>
          }
          @case ('localisation') {
            <div class="ligne-champs">
              <div class="champ"><label>Ville</label><input type="text" name="ville" [(ngModel)]="c.ville" /></div>
              <div class="champ"><label>Pays</label><input type="text" name="pays" [(ngModel)]="c.pays" /></div>
            </div>
            <div class="champ"><label>Zone desservie</label><input type="text" name="zone" [(ngModel)]="c.zone" placeholder="Douala et environs, tout le Cameroun, Afrique centrale…" /></div>
            <div class="champ"><label>Adresse</label><input type="text" name="adresse" [(ngModel)]="c.adresse" /></div>
            <div class="champ"><label>Horaires</label><input type="text" name="horaires" [(ngModel)]="c.horaires" /></div>
          }
          @case ('audience') {
            <div class="champ"><label>Cibles <span class="aide">âge, profil, besoins</span></label>
              <app-puces [(valeurs)]="c.cibles" (valeursChange)="modifie.set(true)" placeholder="Ex. agriculteurs de 25 à 45 ans" /></div>
            <div class="champ"><label>Concurrents</label>
              <app-puces [(valeurs)]="c.concurrents" (valeursChange)="modifie.set(true)" /></div>
          }
          @case ('ton') {
            <div class="ligne-champs">
              <div class="champ"><label>Langue des contenus</label>
                <select name="langue" [(ngModel)]="c.langue"><option value="fr">Français</option><option value="en">Anglais</option></select></div>
              <div class="champ"><label>Adresse au public</label>
                <select name="tutoiement" [(ngModel)]="c.tutoiement"><option [ngValue]="false">Vouvoiement</option><option [ngValue]="true">Tutoiement</option></select></div>
            </div>
            <div class="champ"><label>Fêtes religieuses</label>
              <select name="fetesReligieuses" [(ngModel)]="c.fetesReligieuses">
                <option [ngValue]="true">Proposer les fêtes religieuses (Noël, Pâques, Ramadan, Tabaski…)</option>
                <option [ngValue]="false">Ne pas les proposer</option></select></div>
            <div class="champ"><label>Ton</label><input type="text" name="ton" [(ngModel)]="c.ton" placeholder="Chaleureux et expert, rassurant, dynamique…" /></div>
            <div class="champ"><label>Mots ou sujets à éviter</label><textarea name="eviter" rows="2" [(ngModel)]="c.motsAEviter"></textarea></div>
            <div class="champ"><label>Exemples de publications appréciées</label><textarea name="exemples" rows="4" [(ngModel)]="c.exemples"></textarea></div>
          }
          @case ('visuel') {
            <div class="champ"><label>Couleurs de la marque <span class="aide">codes hex, ex. #2E7D32</span></label>
              <div class="puces" style="margin-bottom:6px">
                @for (col of c.couleurs; track $index) {
                  <span class="puce"><span class="nuance" [style.background]="col"></span>{{ col }}
                    <button type="button" (click)="c.couleurs.splice($index, 1); modifie.set(true)">×</button></span>
                }
              </div>
              <div class="ligne">
                <input type="color" #choix value="#15803d" class="couleur" />
                <button type="button" class="bouton secondaire petit" (click)="ajouterCouleur(choix.value)">Ajouter</button>
              </div>
            </div>
            <div class="champ"><label>Polices</label><input type="text" name="polices" [(ngModel)]="c.polices" /></div>
            <div class="champ"><label>Logo en filigrane sur les photos de produit <span class="aide">mise en page « Photo seule » et carrousel des photos d'un produit</span></label>
              <select name="position-filigrane" [(ngModel)]="c.positionFiligrane" style="max-width:420px">
                <option value="CENTRE">Au centre, discret (conseillé : protège mieux la photo)</option>
                <option value="COIN">Dans le coin en bas à droite</option>
                <option value="AUCUN">Pas de filigrane</option>
              </select></div>
            <h3>Logos</h3>
            @if (!id()) {
              <p class="aide">Enregistre d'abord la fiche pour ajouter des logos et la banque d'images.</p>
            } @else {
              <div class="fichiers">
                @for (a of logos(); track a.id) {
                  <figure>
                    <img [src]="a.url" [alt]="a.legende || a.type" />
                    <figcaption>{{ libelleType(a.type) }}</figcaption>
                    <button type="button" class="bouton danger petit" (click)="supprimerFichier(a)">Supprimer</button>
                  </figure>
                }
              </div>
              <div class="ligne">
                <select #type style="max-width:180px">
                  @for (t of typesFichiers; track t.valeur) {
                    @if (t.valeur !== 'PHOTO') { <option [value]="t.valeur">{{ t.libelle }}</option> }
                  }
                </select>
                <label class="bouton secondaire" style="margin:0">
                  {{ envoiFichier() ? 'Envoi…' : 'Choisir un logo' }}
                  <input type="file" accept="image/png,image/jpeg,image/webp,image/svg+xml" hidden (change)="envoyerFichier($event, type.value)" />
                </label>
              </div>
              <p class="aide">PNG, JPG, WebP ou SVG, 5 Mo maximum.</p>
              <app-banque-images [clientId]="id()!" [repliable]="false" />
            }
          }
          @case ('en-ligne') {
            <div class="ligne-champs">
              <div class="champ"><label>Site web</label><input type="text" name="domaine" [(ngModel)]="c.domaine" placeholder="exemple.com" /></div>
              <div class="champ"><label>WhatsApp Business</label><input type="text" name="whatsapp" [(ngModel)]="c.whatsapp" placeholder="+237 6…" /></div>
            </div>
            @for (r of reseaux; track r) {
              <div class="champ"><label>{{ libellesReseau[r] }}</label>
                <input type="url" [name]="'reseau-' + r" [ngModel]="c.reseaux[r]" (ngModelChange)="c.reseaux[r] = $event" placeholder="Lien de la page" /></div>
            }
            @if (id()) {
              <app-contacts-validation [clientId]="id()!" />
            } @else {
              <p class="aide">Enregistre d'abord la fiche pour définir qui peut valider les publications (codes personnels).</p>
            }
          }
        }
        <div class="actions">
          <span class="aide">{{ modifie() ? 'Modifications non enregistrées' : '' }}</span>
          <span class="espace"></span>
          @if (onglet() !== 'en-ligne') { <button type="button" class="bouton secondaire" (click)="suivant()">Suivant</button> }
          <button class="bouton" [disabled]="envoi() || !c.nom">{{ envoi() ? 'Enregistrement…' : 'Enregistrer' }}</button>
        </div>
      </form>
    </div>
  `,
  styles: `
    .offre { border: 1px solid var(--gris-clair); border-radius: 10px; padding: 8px 12px; margin-bottom: 8px; }
    .offre.ouverte { padding: 10px 14px 14px; border-color: var(--vert, #15803d); }
    .offre-entete { display: flex; gap: 8px; align-items: center; }
    .bascule { flex: 1; min-width: 0; display: flex; gap: 8px; align-items: center; flex-wrap: wrap; background: none; border: 0; padding: 6px 0; font: inherit; text-align: left; cursor: pointer; color: inherit; }
    .bascule:hover strong { text-decoration: underline; }
    .chevron { width: 1em; color: var(--gris); }
    .prix { color: var(--gris); font-size: .85rem; }
    .sans-photo { color: #b45309; }
    .offre-corps { margin-top: 10px; padding-top: 10px; border-top: 1px solid var(--gris-clair); }
    .outils-offres { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin: 8px 0 10px; }
    .recherche-offres { max-width: 220px; }
    .import-catalogue { border: 1px dashed var(--gris-clair); border-radius: 10px; padding: 12px 14px; margin-bottom: 14px; }
    .photos-produit { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }
    .mini { width: 44px; height: 44px; object-fit: cover; border-radius: 6px; border: 1px solid var(--gris-clair); }
    .nuance { width: 14px; height: 14px; border-radius: 4px; display: inline-block; border: 1px solid rgba(0,0,0,.1); }
    .couleur { width: 48px; height: 38px; padding: 2px; border: 1px solid #d1d5db; border-radius: 8px; background: #fff; }
    .images-site { margin-top: 16px; padding-top: 12px; border-top: 1px solid var(--gris-clair); }
    .vignettes { display: flex; flex-wrap: wrap; gap: 10px; }
    .vignette { position: relative; border: 1px solid var(--gris-clair); border-radius: 8px; padding: 4px; cursor: pointer; background: #fff; }
    .vignette img { display: block; width: 104px; height: 78px; object-fit: contain; border-radius: 4px; background: #f3f4f6; }
    .vignette input { position: absolute; top: 8px; left: 8px; z-index: 1; }
    .vignette.aucun { display: flex; align-items: center; gap: 6px; padding: 8px 12px; }
    .vignette.aucun input { position: static; }
    .assistant-site { border-left: 4px solid var(--vert); margin-bottom: 16px; }
    .fichiers { display: grid; grid-template-columns: repeat(auto-fill, minmax(130px, 1fr)); gap: 12px; margin-bottom: 12px; }
    figure { margin: 0; border: 1px solid var(--gris-clair); border-radius: 10px; padding: 8px; text-align: center; }
    figure img { width: 100%; height: 90px; object-fit: contain; background: repeating-conic-gradient(#f3f4f6 0% 25%, #fff 0% 50%) 50% / 16px 16px; border-radius: 6px; }
    figcaption { font-size: .8rem; color: var(--gris); margin: 6px 0; }

    .actions { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; border-top: 1px solid var(--gris-clair); margin-top: 20px; padding-top: 16px; }
  `,
})
export class FicheClientComponent implements OnInit {
  private api = inject(ApiService);
  private confirmation = inject(ConfirmationService);
  private router = inject(Router);

  id = input<string>();
  onglet = signal<Onglet>('identite');
  assets = signal<Asset[]>([]);
  ajouterAsset(a: Asset) {
    this.assets.update(l => [...l, a]);
  }

  photosBanque = computed(() => this.assets().filter(a => a.type === 'PHOTO'));

  /** Offres dépliées (par référence : l'état suit l'offre même si la liste change). Les offres existantes démarrent repliées. */
  private ouvertes = signal<Set<Offre>>(new Set());
  filtreOffres = '';
  nbSansPhoto() {
    return this.c.offres.filter(o => !o.photos?.length).length;
  }

  estOuverte(o: Offre) {
    return this.ouvertes().has(o);
  }

  basculerOffre(o: Offre) {
    this.ouvertes.update(s => { const n = new Set(s); if (n.has(o)) n.delete(o); else n.add(o); return n; });
  }

  toutDeplier() {
    this.ouvertes.set(new Set(this.c.offres));
  }

  toutReplier() {
    this.ouvertes.set(new Set());
  }

  /** Une nouvelle offre s'ouvre tout de suite pour être remplie. */
  ajouterOffre() {
    const o: Offre = { nom: '' };
    this.c.offres.push(o);
    this.filtreOffres = '';
    this.ouvertes.update(s => new Set(s).add(o));
    this.modifie.set(true);
  }

  retirerOffre(o: Offre) {
    const i = this.c.offres.indexOf(o);
    if (i >= 0) this.c.offres.splice(i, 1);
    this.modifie.set(true);
  }

  visibleOffre(o: Offre) {
    const f = this.filtreOffres.trim().toLowerCase();
    return !f || (o.nom ?? '').toLowerCase().includes(f) || (o.categorie ?? '').toLowerCase().includes(f);
  }

  lienCopie = signal('');

  /** Copie le lien wa.me du produit (le produit doit être enregistré dans la fiche). */
  copierLienCommande(o: Offre) {
    const id = this.id();
    if (!id) return;
    this.erreur.set('');
    this.api.lienCommandeProduit(id, o.nom).subscribe({
      next: l => {
        navigator.clipboard?.writeText(l.url).then(() => {
          this.lienCopie.set(o.nom);
          setTimeout(() => this.lienCopie.set(''), 2500);
        });
      },
      error: e => { this.erreur.set(messageErreur(e)); window.scrollTo({ top: 0 }); },
    });
  }

  importCatalogue = signal(false);
  resumeCatalogue = signal<{ texte: string; avertissements: string[]; erreur: boolean } | null>(null);
  photosOuvert = signal<number | null>(null);

  enTexte(l?: string[]) {
    return (l ?? []).join(', ');
  }

  definirListe(o: Offre, champ: 'tailles' | 'couleurs', texte: string) {
    const l = [...new Set(texte.split(/[;,|]/).map(t => t.trim()).filter(Boolean))].slice(0, 20);
    o[champ] = l.length ? l : undefined;
    this.modifie.set(true);
  }

  photosDe(o: Offre) {
    const ids = new Set(o.photos ?? []);
    return this.photosBanque().filter(a => ids.has(a.id));
  }

  /** Lit un catalogue CSV et le fusionne dans la liste : un produit du même nom est mis à jour, les autres sont ajoutés. Rien n'est enregistré. */
  importerCatalogue(ev: Event) {
    const champ = ev.target as HTMLInputElement;
    const fichier = champ.files?.[0];
    const id = this.id();
    if (!fichier || !id) return;
    if (fichier.size > 1024 * 1024) {
      this.resumeCatalogue.set({ texte: 'Fichier trop lourd (1 Mo maximum).', avertissements: [], erreur: true });
      champ.value = '';
      return;
    }
    this.importCatalogue.set(true);
    this.resumeCatalogue.set(null);
    this.api.importerCatalogue(id, fichier).subscribe({
      next: r => {
        let ajoutes = 0;
        let misAJour = 0;
        for (const p of r.produits) {
          const cle = p.nom.trim().toLowerCase();
          const i = this.c.offres.findIndex(o => (o.nom ?? '').trim().toLowerCase() === cle);
          if (i >= 0) {
            const ancien = this.c.offres[i];
            this.c.offres[i] = { ...ancien, ...p, photos: p.photos?.length ? p.photos : ancien.photos };
            misAJour++;
          } else {
            this.c.offres.push(p);
            ajoutes++;
          }
        }
        this.modifie.set(true);
        this.importCatalogue.set(false);
        champ.value = '';
        this.resumeCatalogue.set({
          texte: `${ajoutes} produit(s) ajouté(s), ${misAJour} mis à jour, ${r.photosRattachees} photo(s) rattachée(s). Relis la liste puis enregistre la fiche.`,
          avertissements: r.avertissements, erreur: false,
        });
      },
      error: e => {
        this.importCatalogue.set(false);
        champ.value = '';
        this.resumeCatalogue.set({ texte: messageErreur(e), avertissements: [], erreur: true });
      },
    });
  }

  /** Modèle de tableau à remplir (CSV avec BOM pour qu'Excel affiche bien les accents). */
  telechargerModele() {
    const csv = '\uFEFFnom;prix;description;categorie;tailles;couleurs;stock;photo\n'
      + 'Soutien-gorge Émeraude;12 000 FCFA;Dentelle sans armatures;Soutiens-gorge;"85, 90, 95, 100";"noir, nude";oui;emeraude.jpg\n'
      + 'Culotte Rosé;5 000 FCFA;Coton doux;Culottes;"S, M, L";rose;non;rose.jpg\n';
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
    const lien = document.createElement('a');
    lien.href = url;
    lien.download = 'modele-catalogue.csv';
    lien.click();
    URL.revokeObjectURL(url);
  }

  basculerPhotoProduit(o: Offre, assetId: string) {
    const l = new Set(o.photos ?? []);
    l.has(assetId) ? l.delete(assetId) : l.add(assetId);
    o.photos = [...l];
    this.modifie.set(true);
  }

  envoi = signal(false);
  envoiFichier = signal(false);
  modifie = signal(false);
  siteUrl = '';
  ecraser = false;
  lectureSite = signal(false);
  resumeSite = signal<{ remplis: string[]; ignores: string[]; avertissements: string[] } | null>(null);
  logosTrouves = signal<string[]>([]);
  photosTrouvees = signal<string[]>([]);
  logoChoisi = signal<string | null>(null);
  photosChoisies = signal<Set<string>>(new Set());
  importEnCours = signal(false);
  progression = signal('');
  resumeImport = signal('');
  nbPhotosChoisies = computed(() => this.photosChoisies().size);
  erreur = signal('');
  message = signal('');

  reseaux = RESEAUX;
  libellesReseau = LIBELLES_RESEAU;
  typesFichiers = TYPES_FICHIERS;
  onglets: { id: Onglet; libelle: string }[] = [
    { id: 'identite', libelle: 'Identité' }, { id: 'offres', libelle: 'Offres' },
    { id: 'localisation', libelle: 'Localisation' }, { id: 'audience', libelle: 'Audience' },
    { id: 'ton', libelle: 'Ton' }, { id: 'visuel', libelle: 'Identité visuelle' },
    { id: 'en-ligne', libelle: 'En ligne' },
  ];

  c: Client = {
    nom: '', typeActivite: 'SERVICES', positionFiligrane: 'CENTRE', langue: 'fr', tutoiement: false, fetesReligieuses: true, offres: [], cibles: [], concurrents: [], couleurs: [], reseaux: {},
  };

  ngOnInit() {
    const id = this.id();
    if (id) {
      this.api.client(id).subscribe({
        next: c => {
          this.c = { ...c, typeActivite: c.typeActivite ?? 'SERVICES', positionFiligrane: c.positionFiligrane ?? 'CENTRE', offres: c.offres ?? [], cibles: c.cibles ?? [], concurrents: c.concurrents ?? [],
            couleurs: c.couleurs ?? [], reseaux: c.reseaux ?? {} };
          this.assets.set(c.assets ?? []);
        },
        error: e => this.erreur.set(messageErreur(e)),
      });
    }
  }

  suivant() {
    const i = this.onglets.findIndex(o => o.id === this.onglet());
    this.onglet.set(this.onglets[Math.min(i + 1, this.onglets.length - 1)].id);
    window.scrollTo({ top: 0 });
  }

  ajouterCouleur(valeur: string) {
    const v = valeur.toUpperCase();
    if (!this.c.couleurs.includes(v)) this.c.couleurs.push(v);
    this.modifie.set(true);
  }

  libelleType(t: string) {
    return TYPES_FICHIERS.find(x => x.valeur === t)?.libelle ?? t;
  }

  enregistrer() {
    this.envoi.set(true);
    this.erreur.set('');
    this.message.set('');
    const donnees: Client = { ...this.c, offres: this.c.offres.filter(o => o.nom?.trim()) };
    delete donnees.assets;
    const id = this.id();
    const req = id ? this.api.modifierClient(id, donnees) : this.api.creerClient(donnees);
    req.subscribe({
      next: c => {
        this.envoi.set(false);
        this.modifie.set(false);
        if (!id) {
          this.router.navigate(['/clients', c.id, 'fiche']);
        } else {
          this.message.set('Fiche enregistrée.');
          setTimeout(() => this.message.set(''), 2500);
        }
      },
      error: e => { this.erreur.set(messageErreur(e)); this.envoi.set(false); },
    });
  }

  envoyerFichier(ev: Event, type: string) {
    const champ = ev.target as HTMLInputElement;
    const fichier = champ.files?.[0];
    const id = this.id();
    if (!fichier || !id) return;
    if (fichier.size > 5 * 1024 * 1024) { this.erreur.set('Fichier trop lourd (5 Mo maximum)'); return; }
    this.envoiFichier.set(true);
    this.api.ajouterFichier(id, type, fichier).subscribe({
      next: a => { this.assets.update(l => [...l, a]); this.envoiFichier.set(false); champ.value = ''; },
      error: e => { this.erreur.set(messageErreur(e)); this.envoiFichier.set(false); champ.value = ''; },
    });
  }

  /** Lit le site du client et pré-remplit la fiche : les champs déjà remplis ne sont écrasés que si la case est cochée. */
  lireSite() {
    const url = (this.siteUrl || this.c.domaine || '').trim();
    if (!url) return;
    this.lectureSite.set(true);
    this.erreur.set('');
    this.resumeSite.set(null);
    this.api.analyserSite(url).subscribe({
      next: proposition => {
        this.resumeSite.set(this.appliquerProposition(proposition));
        this.memoriserImages(proposition);
        this.lectureSite.set(false);
      },
      error: e => { this.erreur.set(messageErreur(e)); this.lectureSite.set(false); window.scrollTo({ top: 0 }); },
    });
  }

  /** Pré-remplit la fiche depuis les pages sociales reliées : mêmes règles que pour le site (rien n'est enregistré). */
  lireReseaux() {
    const id = this.id();
    if (!id) return;
    this.lectureSite.set(true);
    this.erreur.set('');
    this.resumeSite.set(null);
    this.api.analyserReseaux(id).subscribe({
      next: proposition => {
        this.resumeSite.set(this.appliquerProposition(proposition));
        this.memoriserImages(proposition);
        this.lectureSite.set(false);
      },
      error: e => { this.erreur.set(messageErreur(e)); this.lectureSite.set(false); window.scrollTo({ top: 0 }); },
    });
  }

  /** Cherche seulement le logo et les photos du site (sans IA : rapide et gratuit). */
  chercherImages() {
    const url = (this.siteUrl || this.c.domaine || '').trim();
    if (!url) return;
    this.lectureSite.set(true);
    this.erreur.set('');
    this.api.analyserSite(url, true).subscribe({
      next: p => {
        this.memoriserImages(p);
        this.lectureSite.set(false);
        if (!p.logos?.length && !p.photos?.length) this.erreur.set('Aucune image exploitable trouvée sur ce site.');
      },
      error: e => { this.erreur.set(messageErreur(e)); this.lectureSite.set(false); window.scrollTo({ top: 0 }); },
    });
  }

  private memoriserImages(p: FicheProposee) {
    this.logosTrouves.set(p.logos ?? []);
    this.photosTrouvees.set(p.photos ?? []);
    this.logoChoisi.set(p.logos?.[0] ?? null);
    this.photosChoisies.set(new Set(p.photos ?? []));
    this.resumeImport.set('');
  }

  /** Une image qui ne se charge pas (lien cassé, accès refusé) est retirée de la liste. */
  ecarter(url: string) {
    this.logosTrouves.update(l => l.filter(u => u !== url));
    this.photosTrouvees.update(l => l.filter(u => u !== url));
    if (this.logoChoisi() === url) this.logoChoisi.set(null);
    this.photosChoisies.update(s => { const n = new Set(s); n.delete(url); return n; });
  }

  basculerPhoto(url: string) {
    this.photosChoisies.update(s => { const n = new Set(s); if (n.has(url)) n.delete(url); else n.add(url); return n; });
  }

  /** Importe le logo et les photos choisis, une image à la fois (progression visible, une erreur n'arrête pas les autres). */
  async importerImages() {
    const id = this.id();
    if (!id) return;
    const taches: { url: string; type: 'PHOTO' | 'LOGO_COULEUR' }[] = [];
    const logo = this.logoChoisi();
    if (logo) taches.push({ url: logo, type: 'LOGO_COULEUR' });
    [...this.photosChoisies()].forEach(url => taches.push({ url, type: 'PHOTO' }));
    this.importEnCours.set(true);
    this.erreur.set('');
    this.resumeImport.set('');
    let reussies = 0;
    const refus: string[] = [];
    for (let i = 0; i < taches.length; i++) {
      this.progression.set(`${i + 1} / ${taches.length}`);
      try {
        const asset = await firstValueFrom(this.api.importerImage(id, taches[i].url, taches[i].type));
        this.assets.update(l => [...l, asset]);
        this.ecarter(taches[i].url);
        reussies++;
      } catch (e) {
        refus.push(messageErreur(e));
      }
    }
    this.importEnCours.set(false);
    this.progression.set('');
    const raisons = [...new Set(refus)].slice(0, 3).join(' ; ');
    this.resumeImport.set(`${reussies} image${reussies > 1 ? 's' : ''} importée${reussies > 1 ? 's' : ''}`
      + (reussies ? ' : le logo est dans l\'onglet « Identité visuelle », les photos sont dans la banque d\'images (elles se décrivent toutes seules).' : '.')
      + (refus.length ? ` ${refus.length} image${refus.length > 1 ? 's' : ''} refusée${refus.length > 1 ? 's' : ''} : ${raisons}.` : ''));
  }

  private appliquerProposition(p: FicheProposee) {
    const remplis: string[] = [];
    const ignores: string[] = [];
    const fiche = this.c as unknown as Record<string, unknown>;
    const vide = (v: unknown) => v === undefined || v === null || v === '' || (Array.isArray(v) && v.length === 0);
    const poser = (libelle: string, cle: string, valeur: unknown) => {
      if (vide(valeur)) return;
      if (!vide(fiche[cle]) && !this.ecraser) { ignores.push(libelle); return; }
      fiche[cle] = valeur;
      remplis.push(libelle);
    };
    poser('nom', 'nom', p.nom);
    poser('secteur', 'secteur', p.secteur);
    poser('description', 'description', p.description);
    poser('slogan', 'slogan', p.slogan);
    poser('année de création', 'anneeCreation', p.anneeCreation);
    poser('ville', 'ville', p.ville);
    poser('pays', 'pays', p.pays);
    poser('zone', 'zone', p.zone);
    poser('adresse', 'adresse', p.adresse);
    poser('horaires', 'horaires', p.horaires);
    poser('ton', 'ton', p.ton);
    poser('offres', 'offres', p.offres);
    poser('cibles', 'cibles', p.cibles);
    poser('couleurs', 'couleurs', p.couleurs);
    poser('site web', 'domaine', p.domaine);
    poser('WhatsApp', 'whatsapp', p.whatsapp);
    // La langue et le tutoiement ont déjà une valeur par défaut : on ne les change que pour un nouveau client ou si la case est cochée
    if (p.langue && p.langue !== this.c.langue && (this.ecraser || !this.id())) { this.c.langue = p.langue; remplis.push('langue'); }
    if (typeof p.tutoiement === 'boolean' && p.tutoiement !== this.c.tutoiement && (this.ecraser || (p.tutoiement && !this.c.tutoiement))) {
      this.c.tutoiement = p.tutoiement;
      remplis.push('tutoiement');
    }
    const nouveauxReseaux = Object.entries(p.reseaux ?? {}).filter(([r, lien]) => lien && (this.ecraser || !this.c.reseaux[r]));
    if (nouveauxReseaux.length) {
      nouveauxReseaux.forEach(([r, lien]) => (this.c.reseaux[r] = lien));
      remplis.push('réseaux sociaux');
    }
    if (remplis.length) this.modifie.set(true);
    return { remplis, ignores, avertissements: p.avertissements ?? [] };
  }

  logos(): Asset[] {
    return this.assets().filter(a => a.type !== 'PHOTO');
  }

  async supprimerFichier(a: Asset) {
    const id = this.id();
    if (!id || !(await this.confirmation.demander({ titre: 'Supprimer ce fichier ?', confirmer: 'Supprimer', danger: true }))) return;
    this.api.supprimerFichier(id, a.id).subscribe({
      next: () => this.assets.update(l => l.filter(x => x.id !== a.id)),
      error: e => this.erreur.set(messageErreur(e)),
    });
  }
}
