package com.okpost.calendrier;

import com.okpost.client.Client;
import com.okpost.client.ClientService;
import com.okpost.client.LienCommande;
import com.okpost.securite.Session;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Liens de commande WhatsApp : pour une publication (son produit) ou pour un produit du catalogue. */
@RestController
@RequestMapping("/api/v1")
public class CommandeController {

    public record LienVue(String url, String message, String produit) {}

    private final CalendrierService calendriers;
    private final ClientService clients;

    public CommandeController(CalendrierService calendriers, ClientService clients) {
        this.calendriers = calendriers;
        this.clients = clients;
    }

    /** Lien lié au produit mis en avant par la publication, ou demande d'information si elle ne porte pas sur un produit. */
    @GetMapping("/publications/{id}/lien-commande")
    public LienVue pourPublication(@PathVariable UUID id) {
        Session s = Session.courante();
        Publication p = calendriers.chargerPublication(s, id);
        Client client = clients.charger(s, calendriers.charger(s, p.getCalendrierId()).getClientId());
        Client.Offre produit = VisuelService.produitMisEnAvant(client, p.getBrief());
        LienCommande.Lien lien = LienCommande.construire(client, produit);
        return new LienVue(lien.url(), lien.message(), produit == null ? null : produit.nom());
    }

    @GetMapping("/clients/{id}/lien-commande")
    public LienVue pourProduit(@PathVariable UUID id, @RequestParam("produit") String nom) {
        Client client = clients.charger(Session.courante(), id);
        Client.Offre produit = VisuelService.offreNommee(client, nom);
        LienCommande.Lien lien = LienCommande.construire(client, produit);
        return new LienVue(lien.url(), lien.message(), produit == null ? null : produit.nom());
    }
}
