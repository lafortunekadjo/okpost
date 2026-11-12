package com.okpost.ia;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.okpost.config.OkpostProperties;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Construit les deux fournisseurs. Aucune auto-configuration Spring AI n'est utilisée :
 * cela permet d'avoir Gemini et Claude côte à côte et de démarrer même sans clé.
 */
@Configuration
public class IaConfig {

    private static final String CLE_ABSENTE = "cle-absente";
    static final String GEMINI_PAR_DEFAUT = "gemini-3.8-flash";

    /** Délai maximum d'un appel (okpost.ia.delai-secondes) : au-delà, l'appel échoue au lieu de bloquer. */
    private static RestClient.Builder clientHttp(OkpostProperties props) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(15));
        f.setReadTimeout(Duration.ofSeconds(Math.max(30, props.ia().delaiSecondes())));
        return RestClient.builder().requestFactory(f);
    }

    /** Une seule relance rapide sur erreur passagère ; les limites de débit sont gérées par FileAttenteIA. */
    private static RetryTemplate relances() {
        return RetryTemplate.builder().maxAttempts(2).fixedBackoff(3000).retryOn(TransientAiException.class).build();
    }

    /** Retire espaces, guillemets et préfixe « models/ » souvent collés par erreur ; valeur par défaut si vide. */
    public static String propre(String valeur, String parDefaut) {
        if (valeur == null) return parDefaut;
        String v = valeur.trim().replaceAll("^[\"']+|[\"']+$", "").trim();
        if (v.startsWith("models/")) v = v.substring("models/".length());
        return v.isEmpty() ? parDefaut : v;
    }

    @Bean
    GenerateurIA generateurGemini(OkpostProperties props, Prompts prompts, FileAttenteIA file, Journal journal, ObjectMapper json) {
        OkpostProperties.Gemini g = props.ia().gemini();
        String cle = propre(g.apiKey(), "");
        boolean configure = !cle.isEmpty();
        String nomModele = propre(g.modele(), GEMINI_PAR_DEFAUT);
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(g.baseUrl())
                .completionsPath("/chat/completions")
                .restClientBuilder(clientHttp(props))
                .apiKey(configure ? cle : CLE_ABSENTE)
                .build();
        OpenAiChatModel modele = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(nomModele).temperature(0.7).build())
                .retryTemplate(relances())
                .build();
        return new GenerateurSpringAi("gemini", modele, configure, nomModele, nomModele,
                nom -> OpenAiChatOptions.builder().model(nom).temperature(0.7).build(),
                prompts, file, journal, json);
    }

    @Bean
    GenerateurIA generateurClaude(OkpostProperties props, Prompts prompts, FileAttenteIA file, Journal journal, ObjectMapper json) {
        OkpostProperties.Claude c = props.ia().claude();
        String cle = propre(c.apiKey(), "");
        boolean configure = !cle.isEmpty();
        String strategie = propre(c.modeleStrategie(), "claude-sonnet-5-5");
        String redaction = propre(c.modeleRedaction(), "claude-haiku-5-5");
        AnthropicApi api = AnthropicApi.builder()
                .apiKey(configure ? cle : CLE_ABSENTE)
                .restClientBuilder(clientHttp(props))
                .build();
        AnthropicChatModel modele = AnthropicChatModel.builder()
                .anthropicApi(api)
                .defaultOptions(AnthropicChatOptions.builder().model(redaction).maxTokens(8192).build())
                .retryTemplate(relances())
                .build();
        return new GenerateurSpringAi("claude", modele, configure, strategie, redaction,
                nom -> AnthropicChatOptions.builder().model(nom).maxTokens(12000).temperature(0.7).build(),
                prompts, file, journal, json);
    }

    @Bean
    GenerateurIA generateurCompatible(OkpostProperties props, Prompts prompts, FileAttenteIA file, Journal journal, ObjectMapper json) {
        OkpostProperties.Compatible c = props.ia().compatible();
        String cle = propre(c.apiKey(), "");
        boolean configure = !cle.isEmpty();
        String nomModele = propre(c.modele(), "openai/gpt-oss-120b");
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(propre(c.baseUrl(), "https://api.groq.com/openai"))
                .completionsPath(propre(c.completionsPath(), "/v1/chat/completions"))
                .restClientBuilder(clientHttp(props))
                .apiKey(configure ? cle : CLE_ABSENTE)
                .build();
        OpenAiChatModel modele = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(nomModele).temperature(0.7).build())
                .retryTemplate(relances())
                .build();
        return new GenerateurSpringAi("compatible", modele, configure, nomModele, nomModele,
                nom -> OpenAiChatOptions.builder().model(nom).temperature(0.7).maxTokens(16000).build(),
                prompts, file, journal, json);
    }
}
