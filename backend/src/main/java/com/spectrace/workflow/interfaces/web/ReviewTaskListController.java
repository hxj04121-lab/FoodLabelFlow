package com.spectrace.workflow.interfaces.web;

import com.spectrace.identity.application.ExternalActorResolver;
import com.spectrace.workflow.application.InvalidReviewTaskQueryException;
import com.spectrace.workflow.application.ReviewTaskReadService;
import com.spectrace.workflow.application.ReviewTaskView;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
public class ReviewTaskListController {
    private final ReviewTaskReadService tasks;
    private final ExternalActorResolver actors;

    public ReviewTaskListController(ReviewTaskReadService tasks, ExternalActorResolver actors) {
        this.tasks = tasks;
        this.actors = actors;
    }

    /** Same active-identity read policy as the existing task detail resource. */
    @GetMapping("/api/review-tasks")
    public List<ReviewTaskView> list(
            @RequestParam(defaultValue = "20") String limit,
            @RequestParam(defaultValue = "0") String offset,
            @RequestParam(required = false) String status,
            HttpServletRequest request) {
        actors.resolve(request.getHeader("X-Auth-Provider"), request.getHeader("X-External-Subject"));
        return tasks.list(integer(limit, "limit"), integer(offset, "offset"), status);
    }

    private static int integer(String value, String field) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException error) {
            throw new InvalidReviewTaskQueryException(field + " must be an integer");
        }
    }
}
