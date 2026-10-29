package com.okpost.abonnement;

/** Formules commerciales : limites de clients, membres et calendriers IA par mois. */
public enum Formule {
    ESSAI(0, 1, 1, 1),
    SOLO(7_500, 3, 1, 3),
    PRO(15_000, 10, 2, 10),
    AGENCE(35_000, 30, 5, 30);

    private final long prixMensuelXaf;
    private final int maxClients;
    private final int maxMembres;
    private final int calendriersParMois;

    Formule(long prixMensuelXaf, int maxClients, int maxMembres, int calendriersParMois) {
        this.prixMensuelXaf = prixMensuelXaf;
        this.maxClients = maxClients;
        this.maxMembres = maxMembres;
        this.calendriersParMois = calendriersParMois;
    }

    public long prixMensuelXaf() { return prixMensuelXaf; }
    public long prixAnnuelXaf() { return prixMensuelXaf * 10; }
    public int maxClients() { return maxClients; }
    public int maxMembres() { return maxMembres; }
    public int calendriersParMois() { return calendriersParMois; }
}
