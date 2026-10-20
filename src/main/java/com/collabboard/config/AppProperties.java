package com.collabboard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String baseUrl, Mail mail, Security security, Registration registration, Storage storage) {

    public record Mail(boolean enabled, String from, String fromName, boolean logCodes) {
    }

    public record Security(String otpSecret, int otpTtlMinutes, int otpMaxAttempts, int otpResendCooldownSeconds,
                           int otpMaxSendsPerHour, int loginMaxFailures, int loginLockMinutes) {
    }

    public record Registration(String studentEmailDomains, String teacherEmailDomains) {
    }

    public record Storage(String root) {
    }
}
