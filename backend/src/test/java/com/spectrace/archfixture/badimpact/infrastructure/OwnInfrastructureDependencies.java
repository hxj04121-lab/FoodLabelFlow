package com.spectrace.archfixture.badimpact.infrastructure;

import com.spectrace.archfixture.badimpact.infrastructure.nested.OwnNestedRepositoryAdapter;

public record OwnInfrastructureDependencies(OwnRepositoryAdapter root, OwnNestedRepositoryAdapter nested) {
}
