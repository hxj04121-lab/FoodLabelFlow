package com.spectrace;

import com.spectrace.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthApiIntegrationTest extends MySqlIntegrationTestSupport {

    @LocalServerPort
    private int port;

    @Test
    void healthEndpointReportsApplicationAndMigratedDatabaseAsHealthy() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/health"))
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("content-type").orElse(""))
                .startsWith("application/json");
        assertThat(response.body())
                .contains("\"status\":\"ok\"")
                .contains("\"database\":\"ok\"");
    }
}
