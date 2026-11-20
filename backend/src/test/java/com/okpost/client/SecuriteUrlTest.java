package com.okpost.client;

import com.okpost.commun.ApiException;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecuriteUrlTest {

    @Test
    void ajouteHttpsEtAccepteUneAdressePublique() {
        assertThat(SecuriteUrl.normaliser("exemple.com")).isEqualTo(URI.create("https://exemple.com"));
        assertThat(SecuriteUrl.normaliser("  http://www.exemple.com/page  ").getHost()).isEqualTo("www.exemple.com");
        assertThat(SecuriteUrl.normaliser("https://exemple.com:443/x").getPort()).isEqualTo(443);
    }

    @Test
    void refuseLesProtocolesLesIdentifiantsEtLesPortsInhabituels() {
        for (String mauvaise : new String[]{"ftp://exemple.com", "file:///etc/passwd", "javascript:alert(1)", "https://user:mdp@exemple.com",
                "http://exemple.com:8080", "http://exemple.com:22", "", "   ", null}) {
            assertThatThrownBy(() -> SecuriteUrl.normaliser(mauvaise)).as(String.valueOf(mauvaise)).isInstanceOf(ApiException.class);
        }
    }

    @Test
    void refuseLesNomsDeMachinesInternes() {
        for (String interne : new String[]{"localhost", "http://localhost/admin", "http://serveur", "http://imprimante.local", "http://app.internal/",
                "http://monsite.localhost"}) {
            assertThatThrownBy(() -> SecuriteUrl.normaliser(interne)).as(interne).isInstanceOf(ApiException.class);
        }
    }

    @Test
    void refuseLesAdressesInternesEtLesServicesDeMetadonnees() throws Exception {
        for (String ip : new String[]{"127.0.0.1", "10.0.0.5", "192.168.1.1", "172.16.0.1", "169.254.169.254", "100.64.0.1", "0.0.0.0",
                "198.18.0.1", "255.255.255.255", "::1", "fd00::1", "fe80::1"}) {
            assertThat(SecuriteUrl.adresseInterdite(InetAddress.getByName(ip))).as(ip).isTrue();
        }
    }

    @Test
    void accepteLesAdressesPubliques() throws Exception {
        for (String ip : new String[]{"8.8.8.8", "1.1.1.1", "93.184.216.34", "2606:4700:4700::1111"}) {
            assertThat(SecuriteUrl.adresseInterdite(InetAddress.getByName(ip))).as(ip).isFalse();
        }
    }

    @Test
    void uneAdresseIpInterneEstRefuseeAvantToutAppel() {
        assertThatThrownBy(() -> SecuriteUrl.verifierHote(URI.create("http://127.0.0.1/")))
                .isInstanceOf(ApiException.class).hasMessageContaining("pas un site public");
        assertThatThrownBy(() -> SecuriteUrl.verifierHote(URI.create("http://169.254.169.254/latest/meta-data/")))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> SecuriteUrl.verifierHote(URI.create("http://[::1]/"))).isInstanceOf(ApiException.class);
    }
}
