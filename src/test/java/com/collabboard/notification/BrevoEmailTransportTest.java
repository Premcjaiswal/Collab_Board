package com.collabboard.notification;

import com.collabboard.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** MAIL_TRANSPORT=brevo sends through Brevo's HTTPS API (Render's free tier blocks SMTP); checked against a local stub. */
class BrevoEmailTransportTest {

    private HttpServer server;
    private final AtomicReference<String> apiKey = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private volatile int status = 201;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/smtp/email", ex -> {
            apiKey.set(ex.getRequestHeaders().getFirst("api-key"));
            body.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] reply = (status == 201 ? "{\"messageId\":\"<x@brevo>\"}" : "{\"code\":\"unauthorized\"}").getBytes();
            ex.sendResponseHeaders(status, reply.length);
            ex.getResponseBody().write(reply);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @SuppressWarnings("unchecked")
    private EmailService service(String key) {
        AppProperties props = new AppProperties("http://localhost",
                new AppProperties.Mail(true, "sender@example.com", "CollabBoard", false, "brevo", key),
                null, null, new AppProperties.Storage("target/x", "filesystem"));
        EmailService email = new EmailService((ObjectProvider<JavaMailSender>) mock(ObjectProvider.class), props);
        email.setBrevoEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/v3/smtp/email");
        return email;
    }

    @Test
    void sendsTheMessageThroughTheApi() throws Exception {
        assertThat(service("test-key-123").send("student@college.edu", "Verify your email", "Your code is 123456", null)).isTrue();
        assertThat(apiKey.get()).isEqualTo("test-key-123");
        JsonNode json = new ObjectMapper().readTree(body.get());
        assertThat(json.at("/sender/email").asText()).isEqualTo("sender@example.com");
        assertThat(json.at("/sender/name").asText()).isEqualTo("CollabBoard");
        assertThat(json.at("/to/0/email").asText()).isEqualTo("student@college.edu");
        assertThat(json.at("/subject").asText()).isEqualTo("Verify your email");
        assertThat(json.at("/textContent").asText()).isEqualTo("Your code is 123456");
    }

    @Test
    void reportsFailureWhenTheApiRejectsTheMessage() {
        status = 401;
        assertThat(service("wrong").send("student@college.edu", "Subject", "Body", null)).isFalse();
    }

    @Test
    void reportsFailureWithoutAnApiKeyAndSendsNothing() {
        assertThat(service("").send("student@college.edu", "Subject", "Body", null)).isFalse();
        assertThat(body.get()).isNull();
    }
}
