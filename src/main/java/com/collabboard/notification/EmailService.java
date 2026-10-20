package com.collabboard.notification;

import com.collabboard.config.AppProperties;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Sends transactional email through the configured SMTP server (Gmail by default).
 * When mail is disabled the message is not sent; delivery failures are logged without
 * secrets and reported to the caller as {@code false} so the UI can offer a retry.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final AppProperties props;

    public EmailService(ObjectProvider<JavaMailSender> mailSender, AppProperties props) {
        this.mailSender = mailSender;
        this.props = props;
    }

    /**
     * @param devLogLine optional line that may be written to the log in development mode only
     *                   (e.g. a one-time code) when mail delivery is disabled
     * @return true when the message was handed to the SMTP server
     */
    public boolean send(String to, String subject, String body, String devLogLine) {
        AppProperties.Mail mail = props.mail();
        if (!mail.enabled()) {
            if (mail.logCodes() && devLogLine != null) {
                log.warn("[DEV MAIL - delivery disabled] to={} subject='{}' {}", to, subject, devLogLine);
            } else {
                log.info("Mail delivery disabled (MAIL_ENABLED=false); message '{}' to {} not sent.", subject, to);
            }
            return false;
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.error("Mail is enabled but no JavaMailSender is configured.");
            return false;
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(mail.from(), mail.fromName(), StandardCharsets.UTF_8.name()));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            sender.send(message);
            return true;
        } catch (Exception ex) {
            // Never log credentials or message bodies (they may contain codes).
            log.error("Failed to send '{}' email to {}: {}", subject, to, ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return false;
        }
    }
}
