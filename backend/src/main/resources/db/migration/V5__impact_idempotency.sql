ALTER TABLE impact_analysis_run
  ADD COLUMN idempotency_key VARCHAR(160) NULL AFTER change_request_id;

UPDATE impact_analysis_run
SET idempotency_key = CONCAT('impact-analysis:', run_code)
WHERE idempotency_key IS NULL;

ALTER TABLE impact_analysis_run
  MODIFY idempotency_key VARCHAR(160) NOT NULL,
  ADD CONSTRAINT uq_impact_run_idempotency_key_v5 UNIQUE (idempotency_key);
