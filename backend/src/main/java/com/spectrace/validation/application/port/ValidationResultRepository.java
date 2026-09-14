package com.spectrace.validation.application.port;

import com.spectrace.validation.domain.ValidationResult;

import java.util.List;

public interface ValidationResultRepository {

    void saveAll(String validationRunId, List<ValidationResult> results);

    List<ValidationResult> findByRunId(String validationRunId);
}
