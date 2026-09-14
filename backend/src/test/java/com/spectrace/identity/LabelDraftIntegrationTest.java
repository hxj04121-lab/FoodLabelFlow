package com.spectrace.identity;

import com.spectrace.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spectrace.dev-external-auth.enabled=true"
)
class LabelDraftIntegrationTest extends MySqlIntegrationTestSupport {

    private static final String PRODUCT_ID =
            "prod_usda_1106285";

    private static final String EXISTING_LABEL_ID =
            "label_1106285_v1";

    private static final Pattern LABEL_ID =
            Pattern.compile(
                    "\"labelVersionId\":\"([^\"]+)\""
            );

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void labelOfficerCreatesDraftFromCurrentFormulaAndReadsIt()
            throws Exception {

        String base = "http://localhost:" + port;

        String currentFormulaId =
                jdbcTemplate.queryForObject(
                        """
                        SELECT current_formula_version_id
                        FROM product
                        WHERE product_id = ?
                        """,
                        String.class,
                        PRODUCT_ID
                );

        Integer previousMaxVersion =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COALESCE(MAX(version_number), 0)
                        FROM label_version
                        WHERE product_id = ?
                          AND jurisdiction_code = 'US'
                        """,
                        Integer.class,
                        PRODUCT_ID
                );

        String body = """
                {
                  "productId": "%s",
                  "jurisdictionCode": "US"
                }
                """.formatted(PRODUCT_ID);

        HttpResponse<String> created;

        try (HttpClient client = HttpClient.newHttpClient()) {

            HttpRequest request =
                    HttpRequest.newBuilder(
                                    URI.create(
                                            base
                                                    + "/api/labels/drafts"
                                    ))
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .header(
                                    "X-Auth-Provider",
                                    "DEV_EXTERNAL"
                            )
                            .header(
                                    "X-External-Subject",
                                    "dev-external-label-officer"
                            )
                            .POST(
                                    HttpRequest.BodyPublishers
                                            .ofString(body)
                            )
                            .build();

            created = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
        }

        assertEquals(
                201,
                created.statusCode(),
                created.body()
        );

        Matcher matcher =
                LABEL_ID.matcher(created.body());

        assertTrue(
                matcher.find(),
                created.body()
        );

        String labelVersionId =
                matcher.group(1);

        String formulaVersionId =
                jdbcTemplate.queryForObject(
                        """
                        SELECT formula_version_id
                        FROM label_version
                        WHERE label_version_id = ?
                        """,
                        String.class,
                        labelVersionId
                );

        String lifecycleStatus =
                jdbcTemplate.queryForObject(
                        """
                        SELECT lifecycle_status
                        FROM label_version
                        WHERE label_version_id = ?
                        """,
                        String.class,
                        labelVersionId
                );

        String createdBy =
                jdbcTemplate.queryForObject(
                        """
                        SELECT created_by_user_id
                        FROM label_version
                        WHERE label_version_id = ?
                        """,
                        String.class,
                        labelVersionId
                );

        Integer versionNumber =
                jdbcTemplate.queryForObject(
                        """
                        SELECT version_number
                        FROM label_version
                        WHERE label_version_id = ?
                        """,
                        Integer.class,
                        labelVersionId
                );

        String isCurrentPublished =
                jdbcTemplate.queryForObject(
                        """
                        SELECT is_current_published
                        FROM label_version
                        WHERE label_version_id = ?
                        """,
                        String.class,
                        labelVersionId
                );

        assertEquals(
                currentFormulaId,
                formulaVersionId
        );

        assertEquals(
                "DRAFT",
                lifecycleStatus
        );

        assertEquals(
                "user_label_officer",
                createdBy
        );

        assertEquals(
                previousMaxVersion + 1,
                versionNumber
        );

        assertEquals(
                "N",
                isCurrentPublished
        );

        try (HttpClient client = HttpClient.newHttpClient()) {

            HttpRequest request =
                    HttpRequest.newBuilder(
                                    URI.create(
                                            base
                                                    + "/api/labels/"
                                                    + labelVersionId
                                    ))
                            .header(
                                    "X-Auth-Provider",
                                    "DEV_EXTERNAL"
                            )
                            .header(
                                    "X-External-Subject",
                                    "dev-external-label-officer"
                            )
                            .GET()
                            .build();

            HttpResponse<String> response =
                    client.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            assertEquals(
                    200,
                    response.statusCode(),
                    response.body()
            );

            assertTrue(
                    response.body().contains(
                            "\"labelVersionId\":\""
                                    + labelVersionId
                                    + "\""
                    )
            );

            assertTrue(
                    response.body().contains(
                            "\"formulaVersionId\":\""
                                    + currentFormulaId
                                    + "\""
                    )
            );

            assertTrue(
                    response.body().contains(
                            "\"lifecycleStatus\":\"DRAFT\""
                    )
            );
        }
    }

    @Test
    void approverWithoutLabelCreateCannotCreateDraft()
            throws Exception {

        String base = "http://localhost:" + port;

        String body = """
                {
                  "productId": "%s",
                  "jurisdictionCode": "US"
                }
                """.formatted(PRODUCT_ID);

        Integer beforeCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM label_version
                        WHERE product_id = ?
                          AND jurisdiction_code = 'US'
                          AND lifecycle_status = 'DRAFT'
                        """,
                        Integer.class,
                        PRODUCT_ID
                );

        try (HttpClient client = HttpClient.newHttpClient()) {

            HttpRequest request =
                    HttpRequest.newBuilder(
                                    URI.create(
                                            base
                                                    + "/api/labels/drafts"
                                    ))
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .header(
                                    "X-Auth-Provider",
                                    "DEV_EXTERNAL"
                            )
                            .header(
                                    "X-External-Subject",
                                    "dev-external-qa-approver"
                            )
                            .POST(
                                    HttpRequest.BodyPublishers
                                            .ofString(body)
                            )
                            .build();

            HttpResponse<String> response =
                    client.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            assertEquals(
                    403,
                    response.statusCode(),
                    response.body()
            );

            assertTrue(
                    response.body().contains(
                            "AUTHORIZATION_DENIED"
                    )
            );
        }

        Integer afterCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM label_version
                        WHERE product_id = ?
                          AND jurisdiction_code = 'US'
                          AND lifecycle_status = 'DRAFT'
                        """,
                        Integer.class,
                        PRODUCT_ID
                );

        assertEquals(
                beforeCount,
                afterCount
        );
    }

    @Test
    void unauthenticatedCallerCannotReadLabelVersion()
            throws Exception {

        String base = "http://localhost:" + port;

        try (HttpClient client = HttpClient.newHttpClient()) {

            HttpRequest request =
                    HttpRequest.newBuilder(
                                    URI.create(
                                            base
                                                    + "/api/labels/"
                                                    + EXISTING_LABEL_ID
                                    ))
                            .GET()
                            .build();

            HttpResponse<String> response =
                    client.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            assertEquals(
                    401,
                    response.statusCode(),
                    response.body()
            );

            assertTrue(
                    response.body().contains(
                            "AUTHENTICATION_REQUIRED"
                    )
            );
        }
    }
}