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

/**
 * Construit les deux fournisseurs. Aucune auto-configuration Spring AI n'est utilisée :
 * cela permet d'avoir Gemini et Claude côte à côte et de démarrer même sans clé.
 */
@Configuration
public class IaConfig {

    private static final String CLE_ABSENTE = "cle-absente";

    @Bean
    GenerateurIA generateurGemini(OkpostProperties props, Prompts prompts, FileAttenteIA file, Journal journal, ObjectMapper json) {
        OkpostProperties.Gemini g = props.ia().gemini();
        boolean configure = g.apiKey() != null && !g.apiKey().isBlank();
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(g.baseUrl())
                .completionsPath("/chat/completions")
                .apiKey(configure ? g.apiKey() : CLE_ABSENTE)
                .build();
        OpenAiChatModel modele = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(g.modele()).temperature(0.7).build())
                .build();
        return new GenerateurSpringAi("gemini", modele, configure, g.modele(), g.modele(),
                nom -> OpenAiChatOptions.builder().model(nom).temperature(0.7).build(),
                prompts, file, journal, json);
    }

    @Bean
    GenerateurIA generateurClaude(OkpostProperties props, Prompts prompts, FileAttenteIA file, Journal journal, ObjectMapper json) {
        OkpostProperties.Claude c = props.ia().claude();
        boolean configure = c.apiKey() != null && !c.apiKey().isBlank();
        AnthropicApi api = AnthropicApi.builder()
                .apiKey(configure ? c.apiKey() : CLE_ABSENTE)
                .build();
        AnthropicChatModel modele = AnthropicChatModel.builder()
                .anthropicApi(api)
                .defaultOptions(AnthropicChatOptions.builder().model(c.modeleRedaction()).maxTokens(8192).build())
                .build();
        return new GenerateurSpringAi("claude", modele, configure, c.modeleStrategie(), c.modeleRedaction(),
                nom -> AnthropicChatOptions.builder().model(nom).maxTokens(12000).temperature(0.7).build(),
                prompts, file, journal, json);
    }
}
