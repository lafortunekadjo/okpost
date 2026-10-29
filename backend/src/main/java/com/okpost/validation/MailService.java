package com.okpost.validation;

import com.okpost.config.OkpostProperties;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/** Envoi d'e-mails simples. Désactivé par défaut (MAIL_ENABLED=false) : les messages sont alors journalisés. */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender expediteur;
    private final OkpostProperties props;

    public MailService(JavaMailSender expediteur, OkpostProperties props) {
        this.expediteur = expediteur;
        this.props = props;
    }

    public boolean actif() {
        return props.mail().actif();
    }

    @Async
    public void envoyer(String destinataire, String sujet, String texte, String lien, String libelleLien) {
        if (!actif()) {
            log.info("E-mail non envoyé (MAIL_ENABLED=false) à {} : {} — {}", destinataire, sujet, lien);
            return;
        }
        try {
            MimeMessage m = expediteur.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(m, false, "UTF-8");
            h.setFrom(props.mail().expediteur());
            h.setTo(destinataire);
            h.setSubject(sujet);
            String html = "<div style=\"font-family:Arial,sans-serif;font-size:15px;color:#1f2937;max-width:520px\">"
                    + "<p>" + HtmlUtils.htmlEscape(texte).replace("\n", "<br>") + "</p>"
                    + (lien == null ? "" : "<p><a href=\"" + HtmlUtils.htmlEscape(lien)
                    + "\" style=\"display:inline-block;background:#16a34a;color:#fff;padding:12px 20px;border-radius:8px;text-decoration:none\">"
                    + HtmlUtils.htmlEscape(libelleLien) + "</a></p>")
                    + "<p style=\"color:#6b7280;font-size:12px\">Envoyé avec OkPost</p></div>";
            h.setText(texte + (lien == null ? "" : "\n\n" + lien), html);
            expediteur.send(m);
        } catch (Exception e) {
            log.error("Échec de l'envoi d'e-mail à {}", destinataire, e);
        }
    }
}
