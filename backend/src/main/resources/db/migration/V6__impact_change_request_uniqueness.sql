-- V5 remains unchanged for databases that already applied the persistence slice.
-- Existing duplicate business keys must be reconciled explicitly; do not delete history.
ALTER TABLE impact_analysis_run
  ADD CONSTRAINT uq_impact_run_change_request_v6 UNIQUE (change_request_id);

UPDATE impact_analysis_run
SET idempotency_key = CONCAT('impact-analysis:', change_request_id);
