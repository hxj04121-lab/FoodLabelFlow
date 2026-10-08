package com.spectrace.workflow.application;

import com.spectrace.workflow.application.port.ReviewTaskReadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Set;

@Service
public class ReviewTaskReadService {
    private static final Set<String> STATUSES = Set.of("OPEN", "IN_REVIEW", "CLOSED");
    private final ReviewTaskReadRepository repository;

    public ReviewTaskReadService(ReviewTaskReadRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ReviewTaskView> list(int limit, int offset, String status) {
        if (limit < 1 || limit > 100) {
            throw new InvalidReviewTaskQueryException("limit must be between 1 and 100");
        }
        if (offset < 0 || offset > 100000) {
            throw new InvalidReviewTaskQueryException("offset must be between 0 and 100000");
        }
        if (status != null && !STATUSES.contains(status)) {
            throw new InvalidReviewTaskQueryException("status must be OPEN, IN_REVIEW or CLOSED");
        }
        return repository.list(limit, offset, status);
    }
}
