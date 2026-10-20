package com.collabboard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String baseUrl, Mail mail, Security security, Registration registration, Storage storage) {

    /** transport: "smtp" (default, JavaMailSender) or "brevo" (HTTPS API, for hosts that block outbound SMTP). */
    public record Mail(boolean enabled, String from, String fromName, boolean logCodes, String transport,
                       String brevoApiKey) {
    }

    public record Security(String otpSecret, int otpTtlMinutes, int otpMaxAttempts, int otpResendCooldownSeconds,
                           int otpMaxSendsPerHour, int loginMaxFailures, int loginLockMinutes) {
    }

    public record Registration(String studentEmailDomains, String teacherEmailDomains) {
    }

    /** type: "filesystem" (default, files under root) or "database" (for hosts without a persistent disk). */
    public record Storage(String root, String type) {
    }
}
