package com.spectrace.impact.application.port;

import com.spectrace.impact.domain.ChangeRequest;

import java.util.Optional;

/** Impact-owned change_request persistence; the adapter maps VersionChange to the typed columns. */
public interface ChangeRequestRepository {

    void save(ChangeRequest changeRequest);

    Optional<ChangeRequest> findById(String changeRequestId);
}
