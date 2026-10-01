-- V5 remains unchanged for databases that already applied the persistence slice.
-- Existing duplicate business keys must be reconciled explicitly; do not delete history.
ALTER TABLE impact_analysis_run
  ADD CONSTRAINT uq_impact_run_change_request_v6 UNIQUE (change_request_id),
  DROP INDEX uq_impact_run_idempotency_key_v5;

-- Old run-code keys can coincide with another row's new change-request key.
-- Guard the business key first, then rebuild this index after the backfill.
UPDATE impact_analysis_run
SET idempotency_key = CONCAT('impact-analysis:', change_request_id)
WHERE idempotency_key <> CONCAT('impact-analysis:', change_request_id);

ALTER TABLE impact_analysis_run
  ADD CONSTRAINT uq_impact_run_idempotency_key_v6 UNIQUE (idempotency_key);
