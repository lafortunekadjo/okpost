import { Component, inject, input, signal, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { ApiService, messageErreur } from '../core/api.service';
import { CalendrierResume, Client, LIBELLES_RESEAU } from '../core/models';

@Component({
  selector: 'app-client',
  imports: [RouterLink, DatePipe],
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

  ngOnInit() {
    this.api.client(this.id()).subscribe({
      next: c => {
        this.client.set(c);
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

  nomsReseaux(r: string[]) {
    return r.map(x => LIBELLES_RESEAU[x] ?? x).join(', ');
  }

  archiver(c: Client) {
    if (!confirm(`Archiver ${c.nom} ? La fiche et ses calendriers ne seront plus affichés.`)) return;
    this.api.archiverClient(c.id!).subscribe({ next: () => this.router.navigateByUrl('/'), error: e => this.erreur.set(messageErreur(e)) });
  }
}
