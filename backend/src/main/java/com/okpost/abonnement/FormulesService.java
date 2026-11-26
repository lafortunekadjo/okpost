package com.okpost.abonnement;

import com.okpost.admin.Actions;
import com.okpost.admin.JournalService;
import com.okpost.commun.ApiException;
import com.okpost.securite.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Prix et limites des formules. Ils sont lus dans la table formule_config (modifiable par le super-admin) ;
 * si une ligne manque, les valeurs d'origine de l'énumération Formule s'appliquent.
 */
@Service
public class FormulesService {

    public record Limites(Formule formule, String nom, long prixMensuelXaf, int maxClients, int maxMembres,
                          int calendriersParMois, int imagesParMois) {
        public long prixAnnuelXaf() { return prixMensuelXaf * 10; }
    }

    public record Modification(String nom, long prixMensuelXaf, int maxClients, int maxMembres, int calendriersParMois, int imagesParMois) {}

    private final FormuleConfigRepository depot;
    private final JournalService journal;

    public FormulesService(FormuleConfigRepository depot, JournalService journal) {
        this.depot = depot;
        this.journal = journal;
    }

    @Transactional(readOnly = true)
    public Limites pour(Formule f) {
        return depot.findById(f.name()).map(c -> vue(f, c)).orElseGet(() -> parDefaut(f));
    }

    @Transactional(readOnly = true)
    public List<Limites> toutes() {
        return Arrays.stream(Formule.values()).map(this::pour).toList();
    }

    @Transactional
    public Limites modifier(Session s, Formule f, Modification m) {
        s.exigerSuperAdmin();
        valider(f, m);
        FormuleConfig c = depot.findById(f.name()).orElseGet(() -> {
            FormuleConfig neuve = new FormuleConfig();
            neuve.setCode(f.name());
            return neuve;
        });
        Limites avant = pour(f);
        c.setNom(m.nom().trim());
        c.setPrixMensuelXaf(f == Formule.ESSAI ? 0 : m.prixMensuelXaf());
        c.setMaxClients(m.maxClients());
        c.setMaxMembres(m.maxMembres());
        c.setCalendriersParMois(m.calendriersParMois());
        c.setImagesParMois(m.imagesParMois());
        c.setUpdatedAt(Instant.now());
        depot.save(c);
        Limites apres = vue(f, c);
        journal.enregistrer(s, Actions.FORMULE_MODIFIEE, f.name(), resume(avant, apres));
        return apres;
    }

    static void valider(Formule f, Modification m) {
        if (m.nom() == null || m.nom().isBlank() || m.nom().trim().length() > 40) {
            throw ApiException.requeteInvalide("Le nom de la formule est obligatoire (40 caractères au plus)");
        }
        if (m.prixMensuelXaf() < 0 || m.prixMensuelXaf() > 10_000_000) throw ApiException.requeteInvalide("Prix mensuel invalide (0 à 10 000 000 FCFA)");
        entre("Clients", m.maxClients(), 1, 1000);
        entre("Membres", m.maxMembres(), 1, 100);
        entre("Calendriers par mois", m.calendriersParMois(), 1, 1000);
        entre("Images IA par mois", m.imagesParMois(), 0, 10_000);
    }

    private static void entre(String libelle, int valeur, int min, int max) {
        if (valeur < min || valeur > max) throw ApiException.requeteInvalide(libelle + " : une valeur entre " + min + " et " + max + " est attendue");
    }

    private static String resume(Limites a, Limites b) {
        StringBuilder sb = new StringBuilder();
        diff(sb, "nom", a.nom(), b.nom());
        diff(sb, "prix", a.prixMensuelXaf(), b.prixMensuelXaf());
        diff(sb, "clients", a.maxClients(), b.maxClients());
        diff(sb, "membres", a.maxMembres(), b.maxMembres());
        diff(sb, "calendriers/mois", a.calendriersParMois(), b.calendriersParMois());
        diff(sb, "images/mois", a.imagesParMois(), b.imagesParMois());
        return sb.length() == 0 ? "aucun changement" : sb.toString();
    }

    private static void diff(StringBuilder sb, String champ, Object avant, Object apres) {
        if (!avant.equals(apres)) sb.append(sb.length() == 0 ? "" : " ; ").append(champ).append(" ").append(avant).append(" → ").append(apres);
    }

    private static Limites vue(Formule f, FormuleConfig c) {
        return new Limites(f, c.getNom(), c.getPrixMensuelXaf(), c.getMaxClients(), c.getMaxMembres(), c.getCalendriersParMois(), c.getImagesParMois());
    }

    static Limites parDefaut(Formule f) {
        String nom = f.name().charAt(0) + f.name().substring(1).toLowerCase();
        return new Limites(f, nom, f.prixMensuelXaf(), f.maxClients(), f.maxMembres(), f.calendriersParMois(), f.imagesParMois());
    }
}
