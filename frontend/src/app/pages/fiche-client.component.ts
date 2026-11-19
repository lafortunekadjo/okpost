import { Component, inject, input, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService, messageErreur } from '../core/api.service';
import { Asset, Client, RESEAUX, LIBELLES_RESEAU } from '../core/models';
import { PucesComponent } from '../shared/puces.component';
import { BanqueImagesComponent } from '../shared/banque-images.component';

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
  imports: [FormsModule, RouterLink, PucesComponent, BanqueImagesComponent],
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

      <div class="onglets" role="tablist">
        @for (o of onglets; track o.id) {
          <button type="button" role="tab" [class.actif]="onglet() === o.id" (click)="onglet.set(o.id)">{{ o.libelle }}</button>
        }
      </div>

      <form class="carte" (ngSubmit)="enregistrer()" (input)="modifie.set(true)">
        @switch (onglet()) {
          @case ('identite') {
            <div class="champ"><label>Nom *</label><input type="text" name="nom" [(ngModel)]="c.nom" required /></div>
            <div class="ligne-champs">
              <div class="champ"><label>Secteur</label><input type="text" name="secteur" [(ngModel)]="c.secteur" placeholder="Agroalimentaire, santé, transport…" /></div>
              <div class="champ"><label>Année de création</label><input type="number" name="annee" [(ngModel)]="c.anneeCreation" /></div>
            </div>
            <div class="champ"><label>Description <span class="aide">ce que fait l'entreprise, pour qui, ce qui la rend différente</span></label>
              <textarea name="description" rows="5" [(ngModel)]="c.description"></textarea></div>
            <div class="champ"><label>Slogan</label><input type="text" name="slogan" [(ngModel)]="c.slogan" /></div>
          }
          @case ('offres') {
            <p class="aide">Produits ou services. Les prix indiqués ici sont les seuls que l'IA pourra citer.</p>
            @for (o of c.offres; track $index) {
              <div class="offre">
                <div class="ligne-champs">
                  <div class="champ"><label>Offre</label><input type="text" [name]="'offre-nom-' + $index" [(ngModel)]="o.nom" /></div>
                  <div class="champ"><label>Prix</label><input type="text" [name]="'offre-prix-' + $index" [(ngModel)]="o.prix" placeholder="25 000 FCFA" /></div>
                </div>
                <div class="champ"><label>Description</label><textarea rows="2" [name]="'offre-desc-' + $index" [(ngModel)]="o.description"></textarea></div>
                <button type="button" class="bouton danger petit" (click)="c.offres.splice($index, 1); modifie.set(true)">Retirer</button>
              </div>
            }
            <button type="button" class="bouton secondaire" (click)="c.offres.push({ nom: '' }); modifie.set(true)">+ Ajouter une offre</button>
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
    .offre { border: 1px solid var(--gris-clair); border-radius: 10px; padding: 14px; margin-bottom: 12px; }
    .nuance { width: 14px; height: 14px; border-radius: 4px; display: inline-block; border: 1px solid rgba(0,0,0,.1); }
    .couleur { width: 48px; height: 38px; padding: 2px; border: 1px solid #d1d5db; border-radius: 8px; background: #fff; }
    .fichiers { display: grid; grid-template-columns: repeat(auto-fill, minmax(130px, 1fr)); gap: 12px; margin-bottom: 12px; }
    figure { margin: 0; border: 1px solid var(--gris-clair); border-radius: 10px; padding: 8px; text-align: center; }
    figure img { width: 100%; height: 90px; object-fit: contain; background: repeating-conic-gradient(#f3f4f6 0% 25%, #fff 0% 50%) 50% / 16px 16px; border-radius: 6px; }
    figcaption { font-size: .8rem; color: var(--gris); margin: 6px 0; }

    .actions { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; border-top: 1px solid var(--gris-clair); margin-top: 20px; padding-top: 16px; }
  `,
})
export class FicheClientComponent implements OnInit {
  private api = inject(ApiService);
  private router = inject(Router);

  id = input<string>();
  onglet = signal<Onglet>('identite');
  assets = signal<Asset[]>([]);
  envoi = signal(false);
  envoiFichier = signal(false);
  modifie = signal(false);
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
    nom: '', langue: 'fr', tutoiement: false, fetesReligieuses: true, offres: [], cibles: [], concurrents: [], couleurs: [], reseaux: {},
  };

  ngOnInit() {
    const id = this.id();
    if (id) {
      this.api.client(id).subscribe({
        next: c => {
          this.c = { ...c, offres: c.offres ?? [], cibles: c.cibles ?? [], concurrents: c.concurrents ?? [],
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

  logos(): Asset[] {
    return this.assets().filter(a => a.type !== 'PHOTO');
  }

  supprimerFichier(a: Asset) {
    const id = this.id();
    if (!id || !confirm('Supprimer ce fichier ?')) return;
    this.api.supprimerFichier(id, a.id).subscribe({
      next: () => this.assets.update(l => l.filter(x => x.id !== a.id)),
      error: e => this.erreur.set(messageErreur(e)),
    });
  }
}
