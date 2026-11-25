package com.example.telecom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.location=file:src/main/resources/", "spring.datasource.url=jdbc:sqlite::memory:"})
@ActiveProfiles("dev")
class NetworkExposureDevProfileTest {

    private static final int OK = 200;

    @Autowired
    private Environment environment;

    @Test
    void apiDocsAreEnabledInDevProfile() throws IOException, InterruptedException {
        assertEquals(OK, statusOf(port(), "/v3/api-docs"), "/v3/api-docs status");
    }

    @Test
    void swaggerUiIsEnabledInDevProfile() throws IOException, InterruptedException {
        assertEquals(OK, statusOf(port(), "/swagger-ui.html"), "/swagger-ui.html status");
    }

    private int port() {
        return environment.getRequiredProperty("local.server.port", Integer.class);
    }

    static int statusOf(int port, String path) throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).build();
            return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        }
    }
}
