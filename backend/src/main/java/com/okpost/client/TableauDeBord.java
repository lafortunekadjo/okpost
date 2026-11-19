package com.okpost.client;

import com.okpost.calendrier.PublicationRepository;
import com.okpost.calendrier.StatutPublication;
import com.okpost.client.ClientDtos.ClientResume;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.Map;

/** Compteurs du mois en cours affichés sur le tableau de bord, par client. */
@Component
public class TableauDeBord {

    private final PublicationRepository publications;
    private final ClientService clients;

    public TableauDeBord(PublicationRepository publications, ClientService clients) {
        this.publications = publications;
        this.clients = clients;
    }

    @Transactional(readOnly = true)
    public ClientResume resume(Client c) {
        YearMonth mois = YearMonth.now();
        LocalDate debut = mois.atDay(1);
        LocalDate fin = mois.atEndOfMonth();
        Map<StatutPublication, Long> n = new EnumMap<>(StatutPublication.class);
        publications.compterParStatut(c.getId(), debut, fin).forEach(x -> n.put(x.getStatut(), x.getNombre()));
        return new ClientResume(c.getId(), c.getNom(), c.getSecteur(), c.getVille(), clients.logoUrl(c.getId()),
                n.getOrDefault(StatutPublication.A_VALIDER, 0L),
                n.getOrDefault(StatutPublication.VALIDEE, 0L),
                n.getOrDefault(StatutPublication.A_MODIFIER, 0L),
                publications.prochainePublication(c.getId(), LocalDate.now()),
                publications.dernierePublication(c.getId()));
    }
}
