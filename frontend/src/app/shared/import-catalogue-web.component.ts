import { Component, inject, input, output, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiService, messageErreur } from '../core/api.service';
import { Asset, Offre, ProduitPropose, ResultatCatalogueWeb } from '../core/models';

interface Ligne { produit: ProduitPropose; coche: boolean; existe: boolean; }

const PHOTOS_MAX = 30;

/**
 * Importe le catalogue depuis le web : le site du client (boutique Shopify, WooCommerce, pages produit) ou les publications
 * de sa Page Facebook / son compte Instagram. L'utilisateur relit et choisit ; rien n'est ajouté avant « Ajouter ».
 * Un produit déjà présent est complété (ses champs remplis ne sont pas écrasés).
 */
@Component({
  selector: 'app-import-catalogue-web',
  template: `
    <div class="import-web">
      <strong>Importer depuis le web</strong>
      <p class="aide">Lit la boutique du site du client (Shopify, WooCommerce, pages produit) ou les publications de sa Page Facebook et de son compte Instagram
        pour proposer les produits, avec leurs prix, tailles, couleurs et photos quand c'est possible. Tu relis puis tu choisis.</p>
      <div class="ligne" style="gap:8px;flex-wrap:wrap">
        <button type="button" class="bouton secondaire" [disabled]="!clientId() || !siteUrl() || enCours()" (click)="depuisSite()">Depuis le site web</button>
        <button type="button" class="bouton secondaire" [disabled]="!clientId() || enCours()" (click)="depuisReseaux()">Depuis Facebook / Instagram</button>
      </div>
      @if (!clientId()) { <p class="aide">Enregistre d'abord la fiche pour pouvoir importer.</p> }
      @else if (!siteUrl()) { <p class="aide">Pour lire le site, renseigne son adresse dans l'onglet « En ligne » ou dans l'assistant en haut de la fiche.</p> }
      @if (enCours()) { <p class="aide">{{ etat() }}</p> }
      @if (erreur()) { <div class="erreur" role="alert" style="margin-top:8px">{{ erreur() }}</div> }

      @if (resultat(); as r) {
        <div class="resultat">
          <p><strong>{{ lignes().length }} produit(s)</strong> trouvé(s) — source : {{ r.source }}.</p>
          @for (w of r.avertissements; track $index) { <p class="aide">· {{ w }}</p> }
          <label class="case"><input type="checkbox" [checked]="avecPhotos()" (change)="avecPhotos.set($any($event.target).checked)" />
            Importer aussi les photos dans la banque d'images ({{ PHOTOS_MAX }} au plus par import)</label>
          <div class="ligne" style="gap:8px;margin:8px 0">
            <button type="button" class="bouton secondaire petit" (click)="toutCocher(true)">Tout cocher</button>
            <button type="button" class="bouton secondaire petit" (click)="toutCocher(false)">Tout décocher</button>
          </div>
          <div class="liste">
            @for (l of lignes(); track $index) {
              <label class="produit">
                <input type="checkbox" [checked]="l.coche" (change)="cocher(l, $any($event.target).checked)" />
                @if (l.produit.imageUrl) { <img [src]="l.produit.imageUrl" alt="" referrerpolicy="no-referrer" loading="lazy" (error)="l.produit.imageUrl = undefined" /> }
                @else { <span class="sans-image">—</span> }
                <span class="infos">
                  <strong>{{ l.produit.nom }}</strong>
                  @if (l.existe) { <span class="pastille">déjà dans le catalogue</span> }
                  <span class="aide">
                    {{ l.produit.prix || 'prix non indiqué' }}
                    @if (l.produit.tailles?.length) { · tailles {{ l.produit.tailles!.join(', ') }} }
                    @if (l.produit.couleurs?.length) { · {{ l.produit.couleurs!.join(', ') }} }
                  </span>
                </span>
              </label>
            }
          </div>
          <div class="ligne" style="gap:8px;margin-top:10px">
            <button type="button" class="bouton" [disabled]="enCours() || nbCoches() === 0" (click)="ajouter()">Ajouter la sélection ({{ nbCoches() }})</button>
            <button type="button" class="bouton secondaire" [disabled]="enCours()" (click)="annuler()">Annuler</button>
          </div>
        </div>
      }
      @if (bilan()) { <div class="succes" role="status" style="margin-top:8px">{{ bilan() }}</div> }
    </div>
  `,
  styles: `
    .import-web { border: 1px dashed var(--gris-clair); border-radius: 10px; padding: 12px 14px; margin-bottom: 14px; }
    .resultat { margin-top: 10px; }
    .liste { display: grid; gap: 6px; max-height: 420px; overflow: auto; }
    .produit { display: flex; gap: 10px; align-items: center; padding: 6px 8px; border: 1px solid var(--gris-clair); border-radius: 8px; cursor: pointer; }
    .produit img, .sans-image { width: 48px; height: 48px; object-fit: cover; border-radius: 6px; flex: none; background: #f3f4f6; display: inline-flex; align-items: center; justify-content: center; color: var(--gris); }
    .infos { display: grid; gap: 2px; min-width: 0; }
    .case { display: flex; gap: 8px; align-items: center; }
  `,
})
export class ImportCatalogueWebComponent {
  private api = inject(ApiService);

  clientId = input<string | undefined>(undefined);
  /** Adresse du site du client (vide : le bouton « Depuis le site web » reste grisé). */
  siteUrl = input<string>('');
  /** Offres de la fiche : les produits importés y sont ajoutés ou complétés. */
  offres = input.required<Offre[]>();
  /** La fiche a des modifications à enregistrer. */
  modifie = output<void>();
  /** Une photo vient d'être ajoutée à la banque d'images : la fiche doit l'afficher. */
  assetAjoute = output<Asset>();

  PHOTOS_MAX = PHOTOS_MAX;
  enCours = signal(false);
  etat = signal('');
  erreur = signal('');
  bilan = signal('');
  resultat = signal<ResultatCatalogueWeb | null>(null);
  lignes = signal<Ligne[]>([]);
  avecPhotos = signal(true);

  nbCoches() {
    return this.lignes().filter(l => l.coche).length;
  }

  depuisSite() {
    const id = this.clientId();
    const url = this.siteUrl().trim();
    if (!id || !url) return;
    this.lancer('Lecture du site (30 secondes au plus)…', this.api.catalogueDepuisSite(id, url));
  }

  depuisReseaux() {
    const id = this.clientId();
    if (!id) return;
    this.lancer('Lecture des publications et recherche des produits par l\'IA…', this.api.catalogueDepuisReseaux(id));
  }

  private lancer(etat: string, appel: ReturnType<ApiService['catalogueDepuisSite']>) {
    this.enCours.set(true);
    this.etat.set(etat);
    this.erreur.set('');
    this.bilan.set('');
    this.resultat.set(null);
    this.lignes.set([]);
    appel.subscribe({
      next: r => {
        this.resultat.set(r);
        this.lignes.set(r.produits.map(produit => ({ produit, coche: true, existe: this.trouver(produit.nom) >= 0 })));
        this.enCours.set(false);
      },
      error: e => { this.erreur.set(messageErreur(e)); this.enCours.set(false); },
    });
  }

  private trouver(nom: string) {
    const cle = nom.trim().toLowerCase();
    return this.offres().findIndex(o => (o.nom ?? '').trim().toLowerCase() === cle);
  }

  cocher(l: Ligne, coche: boolean) {
    l.coche = coche;
    this.lignes.set([...this.lignes()]);
  }

  toutCocher(coche: boolean) {
    this.lignes().forEach(l => (l.coche = coche));
    this.lignes.set([...this.lignes()]);
  }

  annuler() {
    this.resultat.set(null);
    this.lignes.set([]);
    this.erreur.set('');
  }

  async ajouter() {
    const id = this.clientId();
    const choisis = this.lignes().filter(l => l.coche).map(l => l.produit);
    if (!id || !choisis.length) return;
    this.enCours.set(true);
    this.erreur.set('');
    let ajoutes = 0;
    let completes = 0;
    let photos = 0;
    let refusees = 0;
    let aImporter = this.avecPhotos() ? PHOTOS_MAX : 0;
    const raisons = new Set<string>();

    for (let i = 0; i < choisis.length; i++) {
      const p = choisis[i];
      this.etat.set(`Ajout ${i + 1} / ${choisis.length}…`);
      let offre: Offre;
      const idx = this.trouver(p.nom);
      if (idx >= 0) {
        offre = this.offres()[idx];
        if (this.completer(offre, p)) completes++;
      } else {
        offre = { nom: p.nom, description: p.description, prix: p.prix, categorie: p.categorie, tailles: p.tailles, couleurs: p.couleurs,
          disponible: p.disponible === false ? false : undefined };
        this.offres().push(offre);
        ajoutes++;
      }
      // Photo : seulement si le produit n'en a pas déjà une (on n'écrase pas un choix manuel)
      if (aImporter > 0 && p.imageUrl && !offre.photos?.length) {
        aImporter--;
        this.etat.set(`Photo ${PHOTOS_MAX - aImporter} / ${Math.min(PHOTOS_MAX, choisis.length)} : ${p.nom}…`);
        try {
          const asset = await firstValueFrom(this.api.importerImage(id, p.imageUrl, 'PHOTO'));
          this.assetAjoute.emit(asset);
          offre.photos = [asset.id];
          photos++;
        } catch (e) {
          refusees++;
          raisons.add(messageErreur(e));
        }
      }
    }
    this.modifie.emit();
    this.enCours.set(false);
    this.etat.set('');
    this.resultat.set(null);
    this.lignes.set([]);
    const raison = [...raisons].slice(0, 2).join(' ; ');
    this.bilan.set(`${ajoutes} produit(s) ajouté(s), ${completes} complété(s), ${photos} photo(s) importée(s)`
      + (refusees ? ` (${refusees} photo(s) refusée(s) : ${raison})` : '')
      + '. Relis la liste puis enregistre la fiche.');
  }

  /** Complète les champs vides de l'offre existante avec ceux de l'import ; renvoie true si quelque chose a été ajouté. */
  private completer(o: Offre, p: ProduitPropose): boolean {
    let change = false;
    const remplir = <K extends keyof Offre>(cle: K, valeur: Offre[K] | undefined) => {
      const actuel = o[cle];
      const vide = actuel === undefined || actuel === null || actuel === '' || (Array.isArray(actuel) && actuel.length === 0);
      if (vide && valeur !== undefined && valeur !== null && !(Array.isArray(valeur) && valeur.length === 0) && valeur !== '') {
        o[cle] = valeur;
        change = true;
      }
    };
    remplir('description', p.description);
    remplir('prix', p.prix);
    remplir('categorie', p.categorie);
    remplir('tailles', p.tailles);
    remplir('couleurs', p.couleurs);
    if (p.disponible === false && o.disponible === undefined) { o.disponible = false; change = true; }
    return change;
  }
}
