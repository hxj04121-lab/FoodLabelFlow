package com.spectrace.impact.application.port;

import com.spectrace.impact.domain.ReviewTaskLinkage;

import java.util.Optional;

public interface ReviewTaskLinkageRepository {

    ReviewTaskLinkage saveOrGetExisting(ReviewTaskLinkage linkage);

    Optional<ReviewTaskLinkage> findByFindingId(String impactFindingId);
}
