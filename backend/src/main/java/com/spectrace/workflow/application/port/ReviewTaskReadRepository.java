package com.spectrace.workflow.application.port;

import com.spectrace.workflow.application.ReviewTaskView;
import java.util.List;

/** Bounded task collection read; no task or label state transitions. */
public interface ReviewTaskReadRepository {
    List<ReviewTaskView> list(int limit, int offset, String status);
}
