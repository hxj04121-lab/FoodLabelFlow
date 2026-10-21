package com.spectrace;

import com.spectrace.catalog.application.BatchSpecificationAdoptionService;
import com.spectrace.catalog.application.CatalogService;
import com.spectrace.catalog.domain.CatalogFailure;
import com.spectrace.catalog.infrastructure.CatalogStore;
import com.spectrace.catalog.interfaces.web.CatalogController;
import com.spectrace.catalog.interfaces.web.CatalogErrors;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatalogBatchAdoptionHttpTest {
    private static final String PATH = "/api/catalog/specifications/spec_target_v2/formula-adoptions";
    private final CatalogStore store = mock(CatalogStore.class);
    private final CatalogService catalog = mock(CatalogService.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final BatchSpecificationAdoptionService batch = new BatchSpecificationAdoptionService(
            store, catalog, transactions);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CatalogController(catalog, batch))
            .setControllerAdvice(new CatalogErrors()).build();

    @Test
    void deniesMakerBeforeReadingCandidateProductsOrStartingTransactions() throws Exception {
        doThrow(new CatalogFailure(403, "AUTHORIZATION_DENIED", "Required catalog permissions are missing"))
                .when(catalog).requireBatchAdoptionPermission();

        mvc.perform(post(PATH))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTHORIZATION_DENIED"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        verify(catalog).requireBatchAdoptionPermission();
        verifyNoInteractions(store, transactions);
    }
}
