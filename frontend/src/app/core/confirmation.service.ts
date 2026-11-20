import { Injectable, signal } from '@angular/core';

export interface DemandeConfirmation {
  titre: string;
  message?: string;
  /** Libellé du bouton de confirmation (« Supprimer », « Publier maintenant »…). */
  confirmer: string;
  /** Action destructive ou irréversible : le bouton de confirmation est rouge. */
  danger?: boolean;
}

interface DemandeEnCours extends DemandeConfirmation {
  resoudre: (accepte: boolean) => void;
}

/** Remplace les fenêtres confirm() du navigateur par une boîte de dialogue intégrée à l'application. */
@Injectable({ providedIn: 'root' })
export class ConfirmationService {
  demande = signal<DemandeEnCours | null>(null);
  private precedent: HTMLElement | null = null;

  /** Résout à true si l'utilisateur confirme, false s'il annule (bouton, Échap ou clic à côté). */
  demander(d: DemandeConfirmation): Promise<boolean> {
    this.precedent = document.activeElement as HTMLElement | null;
    return new Promise(resoudre => this.demande.set({ ...d, resoudre }));
  }

  repondre(accepte: boolean) {
    const d = this.demande();
    this.demande.set(null);
    d?.resoudre(accepte);
    // Le focus revient là où l'utilisateur était : indispensable au clavier et aux lecteurs d'écran
    setTimeout(() => this.precedent?.focus?.());
  }
}
