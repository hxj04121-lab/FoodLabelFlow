package com.spectrace.impact.application.port;

import com.spectrace.impact.domain.ChangeRequest;
import com.spectrace.impact.domain.ChangeRequest.VersionChange;
import com.spectrace.impact.domain.ChangeType;

import java.util.Optional;

/** Impact-owned change_request persistence; the adapter maps VersionChange to the typed columns. */
public interface ChangeRequestRepository {

    void save(ChangeRequest changeRequest);

    Optional<ChangeRequest> findById(String changeRequestId);

    /**
     * A non-CANCELLED request for exactly this typed version pair, if one exists. Must be a
     * locking (current) read so it sees requests committed while the caller waited on a lock.
     */
    Optional<ChangeRequest> findOpenByVersionChange(ChangeType changeType, VersionChange versionChange);
}
