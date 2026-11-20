import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiService, messageErreur } from '../core/api.service';
import { ConfirmationService } from '../core/confirmation.service';
import { Musique } from '../core/models';

/** Bibliothèque de musiques libres de droits (super-admin) : déposées une fois, choisies ensuite dans chaque carrousel. */
@Component({
  selector: 'app-bibliotheque-musiques',
  template: `
    <div class="carte">
      <h2>Bibliothèque de musiques</h2>
      <p class="aide">Dépose ici des musiques libres de droits : elles apparaissent ensuite dans une liste, avec écoute, quand on monte la vidéo
        d'un carrousel (plus besoin de téléverser un fichier à chaque fois).</p>
      <p class="aide"><strong>Vérifie la licence de chaque piste</strong> avant de la déposer : usage commercial autorisé, et citation de l'auteur
        si elle est exigée. Sources courantes : Pixabay Music, Bibliothèque audio de YouTube, Mixkit, Free Music Archive (filtre CC0 ou CC-BY).
        Une musique commerciale protégée peut faire couper le son d'un Reel par Facebook ou Instagram.</p>

      @if (erreur()) { <div class="erreur">{{ erreur() }}</div> }

      @for (m of pistes(); track m.id) {
        <div class="ligne piste">
          <button type="button" class="bouton secondaire petit" (click)="ecouter(m)">{{ enLecture() === m.id ? 'Arrêter' : 'Écouter' }}</button>
          <strong class="espace">{{ m.nom }}</strong>
          <span class="aide">{{ (m.taille / 1048576).toFixed(1) }} Mo</span>
          <button type="button" class="bouton danger petit" (click)="supprimer(m)">Supprimer</button>
        </div>
      } @empty {
        @if (!chargement()) { <p class="aide">Aucune musique pour l'instant.</p> }
      }

      <div class="ligne" style="margin-top:12px">
        <label class="bouton secondaire" style="margin:0">
          {{ envoi() ? 'Envoi…' : 'Ajouter des musiques' }}
          <input type="file" multiple accept="audio/mpeg,audio/mp4,audio/x-m4a,audio/aac,audio/wav,audio/ogg,.mp3,.m4a,.aac,.ogg,.wav"
                 hidden (change)="ajouter($event)" />
        </label>
        <span class="aide">MP3, M4A, AAC, OGG ou WAV, 15 Mo maximum chacune</span>
      </div>
    </div>
  `,
  styles: [`.piste { padding: 6px 0; border-bottom: 1px solid var(--gris-clair); }`],
})
export class BibliothequeMusiquesComponent implements OnInit, OnDestroy {
  private api = inject(ApiService);
  private confirmation = inject(ConfirmationService);

  pistes = signal<Musique[]>([]);
  erreur = signal('');
  chargement = signal(true);
  envoi = signal(false);
  enLecture = signal<string | null>(null);

  private audio = new Audio();
  private adresse: string | null = null;

  ngOnInit() {
    this.audio.onended = () => this.enLecture.set(null);
    this.recharger();
  }

  ngOnDestroy() {
    this.arreter();
  }

  private recharger() {
    this.api.musiques().subscribe({
      next: l => { this.pistes.set(l); this.chargement.set(false); },
      error: e => { this.erreur.set(messageErreur(e)); this.chargement.set(false); },
    });
  }

  async ajouter(ev: Event) {
    const champ = ev.target as HTMLInputElement;
    const fichiers = Array.from(champ.files ?? []);
    champ.value = '';
    if (!fichiers.length) return;
    this.erreur.set('');
    this.envoi.set(true);
    const refus: string[] = [];
    for (const f of fichiers) {
      if (f.size > 15 * 1024 * 1024) { refus.push(`${f.name} : trop lourde (15 Mo maximum)`); continue; }
      try {
        const m = await firstValueFrom(this.api.ajouterMusique(f));
        this.pistes.update(l => [...l, m].sort((a, b) => a.nom.localeCompare(b.nom)));
      } catch (e) {
        refus.push(`${f.name} : ${messageErreur(e)}`);
      }
    }
    this.envoi.set(false);
    if (refus.length) this.erreur.set(refus.join(' ; '));
  }

  ecouter(m: Musique) {
    if (this.enLecture() === m.id) { this.arreter(); return; }
    this.arreter();
    this.api.ecouterMusique(m.id).subscribe({
      next: blob => {
        this.adresse = URL.createObjectURL(blob);
        this.audio.src = this.adresse;
        this.audio.play().then(() => this.enLecture.set(m.id)).catch(() => this.erreur.set('Lecture impossible dans ce navigateur'));
      },
      error: e => this.erreur.set(messageErreur(e)),
    });
  }

  private arreter() {
    this.audio.pause();
    if (this.adresse) { URL.revokeObjectURL(this.adresse); this.adresse = null; }
    this.enLecture.set(null);
  }

  async supprimer(m: Musique) {
    if (!(await this.confirmation.demander({ titre: `Supprimer « ${m.nom} » de la bibliothèque ?`, confirmer: 'Supprimer', danger: true }))) return;
    if (this.enLecture() === m.id) this.arreter();
    this.api.supprimerMusique(m.id).subscribe({
      next: () => this.pistes.update(l => l.filter(x => x.id !== m.id)),
      error: e => this.erreur.set(messageErreur(e)),
    });
  }
}
