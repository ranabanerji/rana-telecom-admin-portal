package com.example.telecom.web;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.core.env.Environment;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Real HTTP against the real src/main/resources configuration, with Secure
 * cookies turned on as a deployment behind HTTPS would.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.location=file:src/main/resources/", "spring.datasource.url=jdbc:sqlite::memory:",
        "server.servlet.session.cookie.secure=true", "app.auth.admin.password=http-admin-password"})
class HttpHardeningIntegrationTest {

    private static final int OVER_LIMIT = 65 * 1024;
    private static final String JSON = "application/json";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String TICKETS = "/api/tickets";

    @Autowired
    private Environment environment;

    private final HttpClient client = HttpClient.newHttpClient();
    private final Map<String, String> cookies = new LinkedHashMap<>();

    @Test
    void sessionAndCsrfCookiesAreSecure() throws IOException, InterruptedException {
        HttpResponse<String> csrf = send(get("/api/auth/csrf"));
        Assertions.assertTrue(cookieHeader(csrf, "XSRF-TOKEN").contains("Secure"), "XSRF-TOKEN Secure");
        Assertions.assertTrue(cookieHeader(csrf, "XSRF-TOKEN").contains("SameSite=Lax"), "XSRF-TOKEN SameSite");

        HttpResponse<String> login = logIn();
        Assertions.assertEquals(200, login.statusCode(), "login status");
        String session = cookieHeader(login, "JSESSIONID");
        Assertions.assertTrue(session.contains("Secure") && session.contains("HttpOnly"),
                "JSESSIONID should be Secure and HttpOnly: " + session);
    }

    @Test
    void refusesADeclaredBodyOverTheLimit() throws IOException, InterruptedException {
        HttpResponse<String> response = send(post(TICKETS, BodyPublishers.ofString("x".repeat(OVER_LIMIT))));

        Assertions.assertEquals(413, response.statusCode(), "status for a large Content-Length");
    }

    @Test
    void refusesAChunkedBodyOverTheLimit() throws IOException, InterruptedException {
        logIn();
        String body = "{\"customerId\":1,\"subject\":\"Big\",\"priority\":\"LOW\",\"description\":\""
                + "x".repeat(OVER_LIMIT) + "\"}";
        // ofInputStream has no known length, so the client sends it chunked.
        BodyPublisher chunked = BodyPublishers
                .ofInputStream(() -> new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));

        HttpResponse<String> response = send(post(TICKETS, chunked));

        Assertions.assertEquals(413, response.statusCode(), "status for a large chunked body: " + response.body());
    }

    @Test
    void rejectsOverlongFieldsWithFieldErrors() throws IOException, InterruptedException {
        logIn();
        String ticket = "{\"customerId\":1,\"subject\":\"" + "s".repeat(201) + "\",\"priority\":\"LOW\"}";
        String customer = "{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\",\"email\":\"ada@example.com\","
                + "\"phone\":\"555\",\"planId\":1,\"balance\":123456789.123}";

        HttpResponse<String> ticketResponse = send(post(TICKETS, BodyPublishers.ofString(ticket)));
        HttpResponse<String> customerResponse = send(post("/api/customers", BodyPublishers.ofString(customer)));

        Assertions.assertEquals(400, ticketResponse.statusCode(), "ticket status");
        Assertions.assertTrue(ticketResponse.body().contains("\"subject\":\"must be at most 200 characters\""),
                ticketResponse.body());
        Assertions.assertEquals(400, customerResponse.statusCode(), "customer status");
        Assertions.assertTrue(customerResponse.body().contains("\"balance\""), customerResponse.body());
    }

    private HttpResponse<String> logIn() throws IOException, InterruptedException {
        send(get("/api/auth/csrf"));
        String form = "username=admin&password=http-admin-password";
        return send(request("/api/auth/login").header(CONTENT_TYPE, "application/x-www-form-urlencoded")
                .POST(BodyPublishers.ofString(form)));
    }

    private HttpRequest.Builder get(String path) {
        return request(path).GET();
    }

    private HttpRequest.Builder post(String path, BodyPublisher body) {
        return request(path).header(CONTENT_TYPE, JSON).POST(body);
    }

    private HttpRequest.Builder request(String path) {
        int port = environment.getRequiredProperty("local.server.port", Integer.class);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        String token = cookies.get("XSRF-TOKEN");
        if (token != null) {
            builder.header("X-XSRF-TOKEN", token);
        }
        // Java's CookieManager never sends Secure cookies over http, so track them by
        // hand.
        if (!cookies.isEmpty()) {
            builder.header("Cookie", cookies.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("; ")));
        }
        return builder;
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(builder.build(), BodyHandlers.ofString());
        for (String header : response.headers().allValues("Set-Cookie")) {
            String pair = header.split(";", 2)[0];
            int equals = pair.indexOf('=');
            String value = pair.substring(equals + 1);
            if (value.isEmpty()) {
                cookies.remove(pair.substring(0, equals));
            } else {
                cookies.put(pair.substring(0, equals), value);
            }
        }
        return response;
    }

    private static String cookieHeader(HttpResponse<String> response, String name) {
        List<String> matches = response.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith(name + "=")).toList();
        Assertions.assertFalse(matches.isEmpty(), "no " + name + " cookie in " + response.headers().map());
        return matches.get(matches.size() - 1);
    }
}
