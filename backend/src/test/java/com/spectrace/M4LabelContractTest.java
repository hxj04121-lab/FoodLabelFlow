package com.spectrace;

import com.spectrace.identity.application.AuthorizationDeniedException;
import com.spectrace.identity.application.IdentityService;
import com.spectrace.identity.domain.AuthenticatedActor;
import com.spectrace.identity.interfaces.web.IdentityErrors;
import com.spectrace.label.application.LabelDeclarationFacts;
import com.spectrace.label.application.LabelDeclarationQueryService;
import com.spectrace.label.application.LabelDraftService;
import com.spectrace.label.application.port.LabelValidationSnapshot;
import com.spectrace.label.domain.LabelDraft;
import com.spectrace.label.interfaces.web.LabelDraftController;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class M4LabelContractTest {

    private final LabelDraftService service =
            mock(LabelDraftService.class);

    private final IdentityService identityService =
            mock(IdentityService.class);

    private final LabelDeclarationQueryService declarations =
            mock(LabelDeclarationQueryService.class);

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(
                    new LabelDraftController(
                            service,
                            identityService,
                            declarations
                    )
            )
            .setControllerAdvice(new IdentityErrors())
            .build();

    @Test
    void rejectsUnauthenticatedLabelRead() throws Exception {
        mvc.perform(
                        get("/api/labels/label_test")
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsUnauthenticatedLabelCreate() throws Exception {
        mvc.perform(
                        post("/api/labels/drafts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "productId": "product_test",
                                          "jurisdictionCode": "SG"
                                        }
                                        """)
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createsLabelUsingSharedApplicationContract()
            throws Exception {

        AuthenticatedActor actor = actor(
                "user_label_officer",
                "LABEL.CREATE"
        );

        when(identityService.authenticate(
                "demo",
                "label.officer"
        )).thenReturn(actor);

        when(service.createDraft(
                eq("product_test"),
                eq("SG"),
                any(AuthenticatedActor.class)
        )).thenReturn(draft());

        mvc.perform(
                        post("/api/labels/drafts")
                                .header(
                                        "X-Auth-Provider",
                                        "demo"
                                )
                                .header(
                                        "X-External-Subject",
                                        "label.officer"
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "productId": "product_test",
                                          "jurisdictionCode": "SG"
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        "/api/labels/label_contract_test"
                ))
                .andExpect(jsonPath(
                        "$.labelVersionId"
                ).value("label_contract_test"))
                .andExpect(jsonPath(
                        "$.productId"
                ).value("product_test"))
                .andExpect(jsonPath(
                        "$.formulaVersionId"
                ).value("formula_test"))
                .andExpect(jsonPath(
                        "$.ruleSetVersionId"
                ).value("ruleset_test"))
                .andExpect(jsonPath(
                        "$.jurisdictionCode"
                ).value("SG"))
                .andExpect(jsonPath(
                        "$.lifecycleStatus"
                ).value("DRAFT"));

        verify(service).createDraft(
                "product_test",
                "SG",
                actor
        );
    }

    @Test
    void readsLabelUsingSharedApplicationContract()
            throws Exception {

        when(identityService.authenticate(
                "demo",
                "label.officer"
        )).thenReturn(actor(
                "user_label_officer",
                "LABEL.CREATE"
        ));

        when(service.getById(
                "label_contract_test"
        )).thenReturn(draft());

        mvc.perform(
                        get("/api/labels/label_contract_test")
                                .header(
                                        "X-Auth-Provider",
                                        "demo"
                                )
                                .header(
                                        "X-External-Subject",
                                        "label.officer"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.labelVersionId"
                ).value("label_contract_test"))
                .andExpect(jsonPath(
                        "$.formulaVersionId"
                ).value("formula_test"))
                .andExpect(jsonPath(
                        "$.ruleSetVersionId"
                ).value("ruleset_test"))
                .andExpect(jsonPath(
                        "$.versionNumber"
                ).value(1))
                .andExpect(jsonPath(
                        "$.lifecycleStatus"
                ).value("DRAFT"));
    }

    @Test
    void rejectsLabelCreateWithoutCreatePermission()
            throws Exception {

        AuthenticatedActor actor = actor(
                "user_unauthorized"
        );

        when(identityService.authenticate(
                "demo",
                "unauthorized"
        )).thenReturn(actor);

        when(service.createDraft(
                eq("product_test"),
                eq("SG"),
                any(AuthenticatedActor.class)
        )).thenThrow(
                new AuthorizationDeniedException(
                        "Actor lacks required permission: LABEL.CREATE"
                )
        );

        mvc.perform(
                        post("/api/labels/drafts")
                                .header(
                                        "X-Auth-Provider",
                                        "demo"
                                )
                                .header(
                                        "X-External-Subject",
                                        "unauthorized"
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "productId": "product_test",
                                          "jurisdictionCode": "SG"
                                        }
                                        """)
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void preservesAuthenticatedReadContract()
            throws Exception {

        AuthenticatedActor actor = actor(
                "user_reader"
        );

        when(identityService.authenticate(
                "demo",
                "reader"
        )).thenReturn(actor);

        when(service.getById(
                "label_contract_test"
        )).thenReturn(draft());

        mvc.perform(
                        get("/api/labels/label_contract_test")
                                .header(
                                        "X-Auth-Provider",
                                        "demo"
                                )
                                .header(
                                        "X-External-Subject",
                                        "reader"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.labelVersionId"
                ).value("label_contract_test"))
                .andExpect(jsonPath(
                        "$.lifecycleStatus"
                ).value("DRAFT"));
    }

    @Test
    void rejectsUnauthenticatedDeclarationRead()
            throws Exception {

        mvc.perform(
                        get(
                                "/api/labels/"
                                        + "label_contract_test"
                                        + "/declarations"
                        )
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readsStructuredDeclarationsWithVersionBinding()
            throws Exception {

        AuthenticatedActor actor = actor(
                "user_reader"
        );

        when(identityService.authenticate(
                "demo",
                "reader"
        )).thenReturn(actor);

        when(declarations.getByLabelVersionId(
                "label_contract_test"
        )).thenReturn(declarationFacts());

        mvc.perform(
                        get(
                                "/api/labels/"
                                        + "label_contract_test"
                                        + "/declarations"
                        )
                                .header(
                                        "X-Auth-Provider",
                                        "demo"
                                )
                                .header(
                                        "X-External-Subject",
                                        "reader"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.labelVersionId"
                ).value("label_contract_test"))
                .andExpect(jsonPath(
                        "$.formulaVersionId"
                ).value("formula_test"))
                .andExpect(jsonPath(
                        "$.ruleSetVersionId"
                ).value("ruleset_test"))
                .andExpect(jsonPath(
                        "$.declarations.length()"
                ).value(1))
                .andExpect(jsonPath(
                        "$.declarations[0].allergenId"
                ).value("allergen_milk"))
                .andExpect(jsonPath(
                        "$.declarations[0].declarationType"
                ).value("CONTAINS"))
                .andExpect(jsonPath(
                        "$.declarations[0].declarationSource"
                ).value("LABEL"))
                .andExpect(jsonPath(
                        "$.declarations[0].displayText"
                ).value("Contains milk"));

        verify(declarations).getByLabelVersionId(
                "label_contract_test"
        );
    }

    private LabelDeclarationFacts declarationFacts() {
        return new LabelDeclarationFacts(
                "label_contract_test",
                "formula_test",
                "ruleset_test",
                List.of(
                        new LabelValidationSnapshot.AllergenDeclaration(
                                "allergen_milk",
                                "CONTAINS",
                                "LABEL",
                                "Contains milk"
                        )
                )
        );
    }

    private AuthenticatedActor actor(
            String userId,
            String... permissions
    ) {
        return new AuthenticatedActor(
                userId,
                userId,
                userId,
                Set.of(),
                Set.of(permissions)
        );
    }

    private LabelDraft draft() {
        return new LabelDraft(
                "label_contract_test",
                "product_test",
                "formula_test",
                "ruleset_test",
                "SG",
                1,
                "milk, sugar",
                "DRAFT",
                "N",
                "user_label_officer",
                LocalDateTime.of(
                        2026,
                        9,
                        25,
                        12,
                        0
                ),
                "prov_test"
        );
    }
}