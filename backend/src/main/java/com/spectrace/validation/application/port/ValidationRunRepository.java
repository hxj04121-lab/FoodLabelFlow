package com.spectrace.validation.application.port;

import com.spectrace.validation.domain.ValidationRun;

import java.util.Optional;

public interface ValidationRunRepository {

    void save(ValidationRun validationRun);

    Optional<ValidationRun> findById(String validationRunId);
}
