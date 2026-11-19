import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { concatMap, delay, firstValueFrom, from } from 'rxjs';
import { ApiService, messageErreur } from '../core/api.service';
import { Asset } from '../core/models';

const COTE_MAX = 2000;
const POIDS_MAX = 5 * 1024 * 1024;

/**
 * Banque d'images d'un client : ses photos, décrites (par l'IA ou à la main) pour être réutilisées dans les affiches
 * à la place d'une image générée quand le sujet correspond.
 */
@Component({
  selector: 'app-banque-images',
  imports: [FormsModule],
  template: `
    <div class="carte banque">
      <div class="entete-banque">
        <h2>Banque d'images</h2>
        @if (photos().length) { <span class="aide">{{ photos().length }} photo{{ photos().length > 1 ? 's' : '' }} · {{ decrites() }} décrite{{ decrites() > 1 ? 's' : '' }}</span> }
        @if (photos().length && repliable()) {
          <button type="button" class="bouton secondaire petit" [attr.aria-expanded]="ouverte()" (click)="ouverte.set(!ouverte())">
            {{ ouverte() ? 'Réduire' : 'Voir les photos' }}
          </button>
        }
      </div>
      <p class="aide">Ajoute les vraies photos du client (produits, équipe, lieux, événements). OkPost les décrit, puis les réutilise dans les
        affiches quand le sujet de la publication correspond, au lieu de générer une image. Plus la banque est fournie et variée, plus les
        publications ressemblent au client.</p>

      @if (erreur()) { <div class="erreur">{{ erreur() }}</div> }

      <div class="zone" [class.survol]="survol()" (dragover)="survoler($event)" (dragleave)="survol.set(false)" (drop)="deposer($event)">
        @if (envoi(); as e) {
          <strong>Envoi en cours… {{ e.fait }} / {{ e.total }}</strong>
        } @else {
          <span>Glisse des photos ici, ou</span>
          <label class="bouton secondaire" style="margin:0">
            Choisir des photos
            <input type="file" accept="image/*" multiple hidden (change)="choisir($event)" />
          </label>
        }
        <span class="aide">JPG, PNG ou WebP. Les photos sont réduites à {{ cote }} px et converties en JPG avant l'envoi.</span>
      </div>

      @if (photos().length && (ouverte() || !repliable())) {
        <div class="barre">
          <strong>{{ photos().length }} photo{{ photos().length > 1 ? 's' : '' }}</strong>
          <span class="aide">{{ decrites() }} décrite{{ decrites() > 1 ? 's' : '' }}</span>
          <input type="search" class="filtre" placeholder="Filtrer par mot-clé" [ngModel]="filtre()" (ngModelChange)="filtre.set($event); affichees.set(12)" />
          @if (adecrire().length) {
            <button type="button" class="bouton secondaire petit" [disabled]="lot()" (click)="decrireTout()">
              {{ lot() ? 'Description en cours…' : 'Décrire les ' + adecrire().length + ' photos non décrites' }}
            </button>
          }
        </div>

        <div class="grille">
          @for (a of visibles(); track a.id; let i = $index) {
            <figure>
              <img [src]="a.url" [alt]="a.description || a.nomOrigine || 'Photo'" loading="lazy" />
              <p class="etat" [class.ok]="a.analyse === 'OK'" [class.ecart]="ecartee(a)">{{ etat(a) }}</p>
              <textarea rows="2" placeholder="Ce que montre la photo" [attr.aria-label]="'Description de la photo ' + (i + 1)" [value]="a.description ?? ''"
                (change)="maj(a, { description: valeur($event) })"></textarea>
              <input type="text" placeholder="mots-clés, séparés par des virgules" [attr.aria-label]="'Mots-clés de la photo ' + (i + 1)" [value]="motsAffiches(a)"
                (change)="maj(a, { tags: motsCles($event) })" />
              <div class="actions">
                <button type="button" class="bouton secondaire petit" [disabled]="a.analyse === 'EN_COURS'" [attr.aria-label]="'Générer la description de la photo ' + (i + 1)" (click)="decrire(a)">Décrire avec l'IA</button>
                @if (ecartee(a)) { <button type="button" class="bouton secondaire petit" [attr.aria-label]="'Utiliser la photo ' + (i + 1) + ' malgré tout'" (click)="reactiver(a)">Utiliser quand même</button> }
                <button type="button" class="bouton danger petit" [attr.aria-label]="'Supprimer la photo ' + (i + 1)" (click)="supprimer(a)">Supprimer</button>
              </div>
            </figure>
          } @empty {
            <p class="aide">Aucune photo ne correspond à « {{ filtre() }} ».</p>
          }
        </div>
        @if (restantes() > 0) {
          <div class="ligne" style="justify-content:center;margin-top:14px">
            <button type="button" class="bouton secondaire" (click)="affichees.set(affichees() + 12)">
              Afficher plus de photos ({{ restantes() }} restante{{ restantes() > 1 ? 's' : '' }})
            </button>
          </div>
        }
      } @else if (!chargement() && !photos().length) {
        <p class="aide" style="margin-top:12px">La banque est vide : les visuels seront générés par l'IA.</p>
      }
    </div>
  `,
  styles: [`
    .entete-banque { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }
    .entete-banque h2 { margin: 0; }
    .entete-banque button { margin-left: auto; }
    .zone { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; justify-content: center; padding: 18px; margin: 12px 0;
      border: 2px dashed var(--gris-clair); border-radius: 12px; text-align: center; }
    .zone.survol { border-color: var(--principal, #2563eb); background: rgba(37, 99, 235, .06); }
    .barre { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; margin: 8px 0 12px; }
    .filtre { max-width: 220px; margin-left: auto; }
    .grille { display: grid; grid-template-columns: repeat(auto-fill, minmax(210px, 1fr)); gap: 14px; }
    figure { margin: 0; border: 1px solid var(--gris-clair); border-radius: 12px; padding: 10px; }
    figure img { width: 100%; height: 150px; object-fit: cover; border-radius: 8px; background: #f3f4f6; }
    figure textarea, figure input[type=text] { width: 100%; font-size: .8rem; margin-top: 6px; }
    .etat { font-size: .75rem; color: var(--gris); margin: 6px 0 0; }
    .etat.ok { color: #15803d; }
    .etat.ecart { color: #b45309; }
    .actions { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 8px; }
  `],
})
export class BanqueImagesComponent implements OnInit {
  private api = inject(ApiService);

  clientId = input.required<string>();
  /** Repliée par défaut sur la page du client (elle peut contenir des dizaines de photos) ; ouverte dans la fiche. */
  repliable = input(true);
  ouverte = signal(false);
  affichees = signal(12);

  photos = signal<Asset[]>([]);
  filtre = signal('');
  erreur = signal('');
  envoi = signal<{ fait: number; total: number } | null>(null);
  chargement = signal(true);
  survol = signal(false);
  lot = signal(false);
  cote = COTE_MAX;

  filtrees = computed(() => {
    const f = this.sansAccents(this.filtre().trim());
    if (!f) return this.photos();
    return this.photos().filter(a =>
      this.sansAccents([a.description, a.nomOrigine, a.legende, ...(a.tags ?? [])].filter(Boolean).join(' ')).includes(f));
  });
  visibles = computed(() => this.filtrees().slice(0, this.affichees()));
  restantes = computed(() => Math.max(0, this.filtrees().length - this.affichees()));
  decrites = computed(() => this.photos().filter(a => !!a.description?.trim()).length);
  adecrire = computed(() => this.photos().filter(a => !a.description?.trim() && a.analyse !== 'EN_COURS'));

  ngOnInit() {
    this.recharger();
  }

  private recharger(suite?: () => void) {
    this.api.client(this.clientId()).subscribe({
      next: c => {
        this.photos.set((c.assets ?? []).filter(a => a.type === 'PHOTO'));
        this.chargement.set(false);
        suite?.();
      },
      error: e => { this.erreur.set(messageErreur(e)); this.chargement.set(false); },
    });
  }

  // ---------- Envoi ----------

  survoler(ev: DragEvent) {
    ev.preventDefault();
    this.survol.set(true);
  }

  deposer(ev: DragEvent) {
    ev.preventDefault();
    this.survol.set(false);
    this.envoyer(Array.from(ev.dataTransfer?.files ?? []));
  }

  choisir(ev: Event) {
    const champ = ev.target as HTMLInputElement;
    this.envoyer(Array.from(champ.files ?? []));
    champ.value = '';
  }

  private async envoyer(fichiers: File[]) {
    if (!fichiers.length || this.envoi()) return;
    this.erreur.set('');
    const refus: string[] = [];
    this.envoi.set({ fait: 0, total: fichiers.length });
    for (const f of fichiers) {
      try {
        const pret = await this.preparer(f);
        const asset = await firstValueFrom(this.api.ajouterFichier(this.clientId(), 'PHOTO', pret));
        this.photos.update(l => [...l, asset]);
      } catch (e) {
        refus.push(`${f.name} : ${e instanceof Error ? e.message : messageErreur(e)}`);
      }
      this.envoi.update(v => (v ? { ...v, fait: v.fait + 1 } : v));
    }
    this.envoi.set(null);
    this.ouverte.set(true);
    if (refus.length) this.erreur.set(`${refus.length} fichier${refus.length > 1 ? 's' : ''} non ajouté${refus.length > 1 ? 's' : ''} : ${refus.join(' ; ')}`);
    this.suivre();
  }

  /** Réduit la photo (2000 px max) et la convertit en JPG : évite le plafond de 5 Mo et les formats que le serveur ne lit pas. */
  private async preparer(f: File): Promise<File> {
    if (!f.type.startsWith('image/') || f.type === 'image/svg+xml') throw new Error('ce n\'est pas une photo');
    let image: ImageBitmap;
    try {
      image = await createImageBitmap(f);
    } catch {
      throw new Error('format non lu par le navigateur (enregistre-la en JPG ou PNG)');
    }
    try {
      for (const [cote, qualite] of [[COTE_MAX, 0.88], [1600, 0.8], [1200, 0.7]] as const) {
        const e = Math.min(1, cote / Math.max(image.width, image.height));
        const w = Math.max(1, Math.round(image.width * e));
        const h = Math.max(1, Math.round(image.height * e));
        const toile = document.createElement('canvas');
        toile.width = w;
        toile.height = h;
        const g = toile.getContext('2d')!;
        g.fillStyle = '#fff';
        g.fillRect(0, 0, w, h);
        g.drawImage(image, 0, 0, w, h);
        const blob = await new Promise<Blob | null>(r => toile.toBlob(r, 'image/jpeg', qualite));
        if (blob && blob.size <= POIDS_MAX) {
          return new File([blob], f.name.replace(/\.[^.]+$/, '') + '.jpg', { type: 'image/jpeg' });
        }
      }
      throw new Error('trop lourde même après réduction');
    } finally {
      image.close();
    }
  }

  // ---------- Description et édition ----------

  etat(a: Asset): string {
    if (this.ecartee(a)) return 'Écartée de la sélection (peu exploitable)';
    switch (a.analyse) {
      case 'OK': return 'Décrite par l\'IA';
      case 'EN_COURS': return 'Description en cours…';
      case 'ECHEC': return a.description ? 'Décrite à la main' : 'Description automatique impossible : saisis-la';
      default: return a.description ? 'Décrite à la main' : 'Pas encore décrite';
    }
  }

  ecartee(a: Asset): boolean {
    return !!a.tags?.includes('inutilisable');
  }

  valeur(ev: Event): string { return (ev.target as HTMLInputElement | HTMLTextAreaElement).value; }

  motsAffiches(a: Asset): string { return (a.tags ?? []).filter(t => t !== 'inutilisable').join(', '); }

  motsCles(ev: Event): string[] {
    return this.valeur(ev).split(',').map(t => t.trim()).filter(t => t);
  }

  maj(a: Asset, patch: { description?: string; tags?: string[] }) {
    // Le mot-clé réservé « inutilisable » n'est pas affiché : on le garde s'il y était
    if (patch.tags && this.ecartee(a)) patch = { ...patch, tags: [...patch.tags, 'inutilisable'] };
    this.api.modifierFichier(this.clientId(), a.id, patch).subscribe({
      next: m => this.photos.update(l => l.map(x => (x.id === a.id ? m : x))),
      error: e => this.erreur.set(messageErreur(e)),
    });
  }

  /** Remet dans la sélection automatique une photo que l'IA avait écartée. */
  reactiver(a: Asset) {
    this.api.modifierFichier(this.clientId(), a.id, { tags: (a.tags ?? []).filter(t => t !== 'inutilisable') }).subscribe({
      next: m => this.photos.update(l => l.map(x => (x.id === a.id ? m : x))),
      error: e => this.erreur.set(messageErreur(e)),
    });
  }

  decrire(a: Asset) {
    this.api.analyserFichier(this.clientId(), a.id).subscribe({
      next: () => { this.marquerEnCours([a.id]); this.suivre(); },
      error: e => this.erreur.set(messageErreur(e)),
    });
  }

  /** Une demande toutes les 3 secondes, pour rester sous les limites de débit du fournisseur d'IA. */
  decrireTout() {
    const ids = this.adecrire().map(a => a.id);
    if (!ids.length) return;
    this.lot.set(true);
    this.marquerEnCours(ids);
    from(ids).pipe(concatMap(id => this.api.analyserFichier(this.clientId(), id).pipe(delay(3000)))).subscribe({
      error: e => { this.erreur.set(messageErreur(e)); this.lot.set(false); },
      complete: () => { this.lot.set(false); this.suivre(); },
    });
    this.suivre(40);
  }

  private marquerEnCours(ids: string[]) {
    this.photos.update(l => l.map(x => (ids.includes(x.id) ? { ...x, analyse: 'EN_COURS' } : x)));
  }

  /** La description se fait en arrière-plan : on relit la banque tant qu'une photo est en cours. */
  private suivre(essais = 12) {
    if (essais <= 0) return;
    setTimeout(() => this.recharger(() => {
      if (this.photos().some(x => x.analyse === 'EN_COURS')) this.suivre(essais - 1);
    }), 4000);
  }

  supprimer(a: Asset) {
    if (!confirm('Supprimer cette photo de la banque ?')) return;
    this.api.supprimerFichier(this.clientId(), a.id).subscribe({
      next: () => this.photos.update(l => l.filter(x => x.id !== a.id)),
      error: e => this.erreur.set(messageErreur(e)),
    });
  }

  private sansAccents(s: string): string {
    return s.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');
  }
}
