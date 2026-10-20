package com.collabboard.notification;

import com.collabboard.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

/**
 * When email delivery is enabled, verifies at startup that the SMTP server accepts the configured login, and logs
 * the result. It never fails startup and never logs credentials.
 */
@Component
public class MailStartupCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MailStartupCheck.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final AppProperties props;

    public MailStartupCheck(ObjectProvider<JavaMailSender> mailSender, AppProperties props) {
        this.mailSender = mailSender;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.mail().enabled()) {
            log.info("Email delivery is disabled (MAIL_ENABLED=false).");
            return;
        }
        if (props.mail().logCodes()) {
            log.warn("MAIL_LOG_CODES is ignored while email delivery is enabled; codes are never logged.");
        }
        if (EmailService.usesBrevo(props.mail())) {
            // No SMTP connection on this transport; only check that the key is present (never log it).
            if (props.mail().brevoApiKey() == null || props.mail().brevoApiKey().isBlank()) {
                log.error("MAIL_TRANSPORT=brevo but BREVO_API_KEY is empty; emails cannot be sent.");
            } else {
                log.info("Email transport: Brevo HTTPS API, sender {}.", props.mail().from());
            }
            return;
        }
        if (!(mailSender.getIfAvailable() instanceof JavaMailSenderImpl impl)) {
            log.error("Email delivery is enabled but no SMTP sender is configured.");
            return;
        }
        if (impl.getUsername() == null || impl.getUsername().isBlank() || impl.getPassword() == null || impl.getPassword().isBlank()) {
            log.error("Email delivery is enabled but MAIL_USERNAME or MAIL_PASSWORD is empty.");
            return;
        }
        try {
            impl.testConnection();
            log.info("SMTP check passed: {}:{} accepted the login for {}.", impl.getHost(), impl.getPort(), impl.getUsername());
        } catch (Exception ex) {
            // The server's reply (e.g. "535-5.7.8 Username and Password not accepted") never contains the password.
            String reply = ex.getMessage() == null ? "" : ex.getMessage().replaceAll("\\s+", " ").trim();
            log.error("SMTP check FAILED for {}:{} as {} ({}: {}). For Gmail, MAIL_PASSWORD must be a Google App Password.",
                    impl.getHost(), impl.getPort(), impl.getUsername(), ex.getClass().getSimpleName(), reply);
        }
    }
}
