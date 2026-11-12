import { Routes } from '@angular/router';
import { connecteGuard, inviteGuard } from './core/guards';
import { CadreComponent } from './shared/cadre.component';

export const routes: Routes = [
  { path: 'connexion', canActivate: [inviteGuard], loadComponent: () => import('./pages/connexion.component').then(m => m.ConnexionComponent) },
  { path: 'inscription', canActivate: [inviteGuard], loadComponent: () => import('./pages/inscription.component').then(m => m.InscriptionComponent) },
  // Pages publiques exigées par Meta pour la revue de l'application
  { path: 'confidentialite', data: { page: 'confidentialite' }, loadComponent: () => import('./pages/legal.component').then(m => m.LegalComponent) },
  { path: 'conditions', data: { page: 'conditions' }, loadComponent: () => import('./pages/legal.component').then(m => m.LegalComponent) },
  { path: 'suppression-donnees', data: { page: 'suppression-donnees' }, loadComponent: () => import('./pages/legal.component').then(m => m.LegalComponent) },
  {
    path: 'calendriers/:id/export',
    canActivate: [connecteGuard],
    loadComponent: () => import('./pages/export-calendrier.component').then(m => m.ExportCalendrierComponent),
  },
  {
    path: 'clients/:id/bilan',
    canActivate: [connecteGuard],
    loadComponent: () => import('./pages/bilan.component').then(m => m.BilanComponent),
  },
  { path: 'c/:jeton', loadComponent: () => import('./pages/export-calendrier.component').then(m => m.ExportCalendrierComponent) },
  { path: 'v/:jeton', loadComponent: () => import('./pages/portail.component').then(m => m.PortailComponent) },
  {
    path: '',
    component: CadreComponent,
    canActivate: [connecteGuard],
    children: [
      { path: '', loadComponent: () => import('./pages/tableau-de-bord.component').then(m => m.TableauDeBordComponent) },
      { path: 'clients/nouveau', loadComponent: () => import('./pages/fiche-client.component').then(m => m.FicheClientComponent) },
      { path: 'clients/:id', loadComponent: () => import('./pages/client.component').then(m => m.ClientComponent) },
      { path: 'clients/:id/fiche', loadComponent: () => import('./pages/fiche-client.component').then(m => m.FicheClientComponent) },
      { path: 'clients/:id/calendriers/nouveau', loadComponent: () => import('./pages/nouveau-calendrier.component').then(m => m.NouveauCalendrierComponent) },
      { path: 'calendriers/:id/evaluation', loadComponent: () => import('./pages/evaluation.component').then(m => m.EvaluationComponent) },
      { path: 'calendriers/:id', loadComponent: () => import('./pages/calendrier.component').then(m => m.CalendrierComponent) },
      { path: 'publications/:id', loadComponent: () => import('./pages/publication.component').then(m => m.PublicationComponent) },
      { path: 'reglages', loadComponent: () => import('./pages/reglages.component').then(m => m.ReglagesComponent) },
    ],
  },
  { path: '**', redirectTo: '' },
];
