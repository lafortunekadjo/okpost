package com.okpost.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTelechargementTest {

    @Test
    void leCdnDeMetaVoitUnNavigateurLesAutresSitesVoientOkPost() {
        for (String hote : new String[]{"scontent-los4-1.xx.fbcdn.net", "scontent.cdninstagram.com", "lookaside.fbsbx.com", "fbcdn.net"}) {
            assertThat(TelechargeurImage.agentPour(hote)).as(hote).contains("Chrome");
        }
        for (String hote : new String[]{"exemple.com", "monfbcdn.net.evil.com", "cdn.exemple.cm", "evilfbcdn.net", null}) {
            assertThat(TelechargeurImage.agentPour(hote)).as(String.valueOf(hote)).contains("OkPostBot");
        }
    }
}
