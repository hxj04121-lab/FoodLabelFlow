package com.spectrace.archfixture.validation.application;

import com.spectrace.audit.application.port.AuditEventPort;
import com.spectrace.allergen.application.port.AllergenFactsPort;
import com.spectrace.label.application.port.LabelSnapshotPort;
import com.spectrace.validation.application.port.AuthorizationPort;

public class GoodValidationService {
    public GoodValidationService(
            LabelSnapshotPort labels,
            AllergenFactsPort allergens,
            AuthorizationPort authorization,
            AuditEventPort auditEvents
    ) {
    }
}
