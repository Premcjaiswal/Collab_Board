package com.collabboard.notification;

import com.collabboard.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Sends transactional email through the configured SMTP server (Gmail by default), or through Brevo's HTTPS API when
 * {@code MAIL_TRANSPORT=brevo} (for hosts that block outbound SMTP, such as Render's free tier).
 * When mail is disabled the message is not sent; delivery failures are logged without
 * secrets and reported to the caller as {@code false} so the UI can offer a retry.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    static final String BREVO_ENDPOINT = "https://api.brevo.com/v3/smtp/email";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ObjectProvider<JavaMailSender> mailSender;
    private final AppProperties props;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private String brevoEndpoint = BREVO_ENDPOINT;

    public EmailService(ObjectProvider<JavaMailSender> mailSender, AppProperties props) {
        this.mailSender = mailSender;
        this.props = props;
    }

    /** Test hook: point the Brevo transport at a local stub server. */
    void setBrevoEndpoint(String endpoint) {
        this.brevoEndpoint = endpoint;
    }

    static boolean usesBrevo(AppProperties.Mail mail) {
        return "brevo".equalsIgnoreCase(mail.transport());
    }

    /**
     * @param devLogLine optional line that may be written to the log in development mode only
     *                   (e.g. a one-time code) when mail delivery is disabled
     * @return true when the message was accepted by the SMTP server or email API
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
        return usesBrevo(mail) ? sendViaBrevo(mail, to, subject, body) : sendViaSmtp(mail, to, subject, body);
    }

    private boolean sendViaSmtp(AppProperties.Mail mail, String to, String subject, String body) {
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

    private boolean sendViaBrevo(AppProperties.Mail mail, String to, String subject, String body) {
        if (mail.brevoApiKey() == null || mail.brevoApiKey().isBlank()) {
            log.error("MAIL_TRANSPORT=brevo but BREVO_API_KEY is empty; '{}' email to {} not sent.", subject, to);
            return false;
        }
        try {
            String payload = JSON.writeValueAsString(Map.of(
                    "sender", Map.of("email", mail.from(), "name", mail.fromName()),
                    "to", List.of(Map.of("email", to)),
                    "subject", subject,
                    "textContent", body));
            HttpRequest request = HttpRequest.newBuilder(URI.create(brevoEndpoint))
                    .timeout(Duration.ofSeconds(15))
                    .header("api-key", mail.brevoApiKey())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 == 2) {
                return true;
            }
            // Brevo's error body describes the problem (e.g. unverified sender) and never contains the API key.
            String reason = response.body() == null ? "" : response.body().replaceAll("\\s+", " ");
            log.error("Brevo rejected '{}' email to {}: HTTP {} {}", subject, to, response.statusCode(),
                    reason.length() > 300 ? reason.substring(0, 300) : reason);
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while sending '{}' email to {}", subject, to);
            return false;
        } catch (Exception ex) {
            log.error("Failed to send '{}' email to {} via Brevo: {}", subject, to, ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return false;
        }
    }
}
