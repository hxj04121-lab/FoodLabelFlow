package com.spectrace.workflow.interfaces.web;

import com.spectrace.shared.api.ApiError;
import com.spectrace.workflow.application.InvalidReviewTaskQueryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ReviewTaskListController.class)
@Order(Ordered.LOWEST_PRECEDENCE)
public class ReviewTaskReadErrors {
    private static final Logger LOG = LoggerFactory.getLogger(ReviewTaskReadErrors.class);

    @ExceptionHandler(InvalidReviewTaskQueryException.class)
    public ResponseEntity<ApiError> invalid(InvalidReviewTaskQueryException error) {
        return ResponseEntity.badRequest().body(ApiError.of("REVIEW_TASK_QUERY_INVALID", error.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception error) {
        LOG.error("Review task collection read failed", error);
        return ResponseEntity.internalServerError()
                .body(ApiError.of("INTERNAL_ERROR", "The review tasks could not be read"));
    }
}
