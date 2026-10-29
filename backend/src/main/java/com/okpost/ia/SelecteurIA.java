package com.okpost.ia;

import com.okpost.config.OkpostProperties;
import com.okpost.securite.WorkspaceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Choisit le fournisseur IA : réglage de l'espace (formule Agence), sinon réglage de la plateforme en base,
 * sinon propriété okpost.ia.fournisseur. Le super-admin pourra le changer depuis l'interface.
 */
@Component
public class SelecteurIA {

    public static final String CLE = "ia.fournisseur";

    private final Map<String, GenerateurIA> generateurs;
    private final ParametrePlateformeRepository parametres;
    private final WorkspaceRepository workspaces;
    private final OkpostProperties props;

    public SelecteurIA(List<GenerateurIA> liste, ParametrePlateformeRepository parametres,
                       WorkspaceRepository workspaces, OkpostProperties props) {
        this.generateurs = liste.stream().collect(Collectors.toMap(GenerateurIA::nom, Function.identity()));
        this.parametres = parametres;
        this.workspaces = workspaces;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public GenerateurIA pour(UUID workspaceId) {
        String choix = workspaces.findById(workspaceId)
                .map(w -> w.getFournisseurIa())
                .filter(f -> f != null && generateurs.containsKey(f))
                .orElseGet(this::parDefaut);
        return generateurs.get(choix);
    }

    @Transactional(readOnly = true)
    public String parDefaut() {
        return parametres.findById(CLE).map(ParametrePlateforme::getValeur)
                .filter(generateurs::containsKey)
                .orElse(props.ia().fournisseur());
    }

    @Transactional
    public void definirParDefaut(String fournisseur) {
        if (!generateurs.containsKey(fournisseur)) {
            throw com.okpost.commun.ApiException.requeteInvalide("Fournisseur inconnu : " + fournisseur);
        }
        ParametrePlateforme p = parametres.findById(CLE).orElseGet(() -> new ParametrePlateforme(CLE, fournisseur));
        p.setValeur(fournisseur);
        parametres.save(p);
    }

    public Map<String, Boolean> etat() {
        return generateurs.values().stream().collect(Collectors.toMap(GenerateurIA::nom, GenerateurIA::configure));
    }
}
