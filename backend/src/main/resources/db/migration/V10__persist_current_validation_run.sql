-- Persist a current validation association instead of guessing creation order
-- from second-precision timestamps and random run identifiers. Existing runs,
-- findings, audit, approvals and publications remain unchanged.
ALTER TABLE validation_run
  ADD CONSTRAINT uq_validation_run_target_v10
    UNIQUE (validation_run_id, label_version_id, rule_set_version_id);
CREATE INDEX idx_validation_run_target_v10
  ON validation_run(label_version_id, rule_set_version_id, validation_run_id);

CREATE TABLE validation_current_run (
  label_version_id VARCHAR(120) NOT NULL,
  rule_set_version_id VARCHAR(100) NOT NULL,
  validation_run_id VARCHAR(140),
  current_sequence BIGINT UNSIGNED NOT NULL DEFAULT 0,
  origin VARCHAR(24) NOT NULL,
  observed_run_count BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (label_version_id, rule_set_version_id),
  CONSTRAINT fk_validation_current_label_v10
    FOREIGN KEY (label_version_id) REFERENCES label_version(label_version_id)
    ON DELETE CASCADE,
  CONSTRAINT fk_validation_current_rules_v10
    FOREIGN KEY (rule_set_version_id) REFERENCES rule_set_version(rule_set_version_id)
    ON DELETE CASCADE,
  CONSTRAINT fk_validation_current_run_v10
    FOREIGN KEY (validation_run_id, label_version_id, rule_set_version_id)
    REFERENCES validation_run(validation_run_id, label_version_id, rule_set_version_id)
    ON DELETE CASCADE,
  CONSTRAINT chk_validation_current_origin_v10
    CHECK (origin IN ('LEGACY_UNIQUE', 'LEGACY_AMBIGUOUS', 'RECORDED'))
);

-- Ledger registration records identity only for legacy runs; no historical
-- causal sequence is manufactured. New registrations only accompany INSERT.
CREATE TABLE validation_run_registration (
  validation_run_id VARCHAR(140) NOT NULL PRIMARY KEY,
  label_version_id VARCHAR(120) NOT NULL,
  rule_set_version_id VARCHAR(100) NOT NULL,
  origin VARCHAR(16) NOT NULL,
  registration_sequence BIGINT UNSIGNED,
  CONSTRAINT fk_validation_registration_target_v10
    FOREIGN KEY (validation_run_id, label_version_id, rule_set_version_id)
    REFERENCES validation_run(validation_run_id, label_version_id, rule_set_version_id)
    ON DELETE CASCADE,
  CONSTRAINT chk_validation_registration_origin_v10
    CHECK ((origin = 'LEGACY' AND registration_sequence IS NULL)
      OR (origin = 'RECORDED' AND registration_sequence IS NOT NULL AND registration_sequence > 0))
);

INSERT INTO validation_run_registration(
  validation_run_id, label_version_id, rule_set_version_id, origin, registration_sequence
)
SELECT validation_run_id, label_version_id, rule_set_version_id, 'LEGACY', NULL
FROM validation_run;

-- Historical creation order cannot be recovered for a tied maximum second.
-- Retain the uniquely latest timestamp only; any tie requires a new run.
-- Sequence zero denotes a legacy association, never a manufactured order.
INSERT INTO validation_current_run(
  label_version_id, rule_set_version_id, validation_run_id, current_sequence, origin, observed_run_count
)
SELECT vr.label_version_id, vr.rule_set_version_id,
       CASE WHEN COUNT(*) = 1 THEN MIN(vr.validation_run_id) ELSE NULL END,
       0,
       CASE WHEN COUNT(*) = 1 THEN 'LEGACY_UNIQUE' ELSE 'LEGACY_AMBIGUOUS' END,
       MAX(latest.run_count)
FROM validation_run vr
JOIN (
  SELECT label_version_id, rule_set_version_id, MAX(ran_at) AS latest_ran_at, COUNT(*) AS run_count
  FROM validation_run
  GROUP BY label_version_id, rule_set_version_id
) latest
  ON latest.label_version_id = vr.label_version_id
 AND latest.rule_set_version_id = vr.rule_set_version_id
 AND latest.latest_ran_at = vr.ran_at
GROUP BY vr.label_version_id, vr.rule_set_version_id;

DROP PROCEDURE IF EXISTS sp_insert_validation_run;

DELIMITER $$

-- Requires the caller's transaction. No START TRANSACTION or COMMIT here:
-- Java run/results/audit and legacy fixture helpers own the complete commit.
-- An existing run cannot be registered later: supported writers must INSERT
-- a fresh identity in this routine before changing the current association.
CREATE PROCEDURE sp_insert_validation_run(
  IN p_validation_run_id VARCHAR(140),
  IN p_label_version_id VARCHAR(120),
  IN p_rule_set_version_id VARCHAR(100),
  IN p_status VARCHAR(40),
  IN p_ran_by_user_id VARCHAR(80),
  IN p_ran_at DATETIME,
  IN p_summary VARCHAR(1000),
  IN p_data_provenance_id VARCHAR(100)
)
BEGIN
  DECLARE v_product_id VARCHAR(100);
  DECLARE v_locked_product_id VARCHAR(100);
  DECLARE v_locked_label_id VARCHAR(120);
  DECLARE v_observed_run_count BIGINT UNSIGNED;

  SELECT product_id INTO v_product_id
  FROM label_version WHERE label_version_id = p_label_version_id;
  IF v_product_id IS NOT NULL THEN
    SELECT product_id INTO v_locked_product_id
    FROM product WHERE product_id = v_product_id FOR UPDATE;
    -- Hold the parent lock before INSERT. Even an unsupported raw child INSERT
    -- must wait for the label FK lock until this transaction has committed.
    SELECT label_version_id INTO v_locked_label_id
    FROM label_version WHERE label_version_id = p_label_version_id FOR UPDATE;
  END IF;

  INSERT INTO validation_run(
    validation_run_id, label_version_id, rule_set_version_id, status,
    ran_by_user_id, ran_at, summary, data_provenance_id
  ) VALUES (
    p_validation_run_id, p_label_version_id, p_rule_set_version_id, p_status,
    p_ran_by_user_id, p_ran_at, p_summary, p_data_provenance_id
  );

  SELECT COUNT(*) INTO v_observed_run_count
  FROM validation_run FORCE INDEX (idx_validation_run_target_v10)
  WHERE label_version_id = p_label_version_id
    AND rule_set_version_id = p_rule_set_version_id
  FOR SHARE;

  INSERT INTO validation_current_run(
    label_version_id, rule_set_version_id, validation_run_id,
    current_sequence, origin, observed_run_count
  ) VALUES (
    p_label_version_id, p_rule_set_version_id, p_validation_run_id,
    1, 'RECORDED', v_observed_run_count
  )
  ON DUPLICATE KEY UPDATE
    validation_run_id = p_validation_run_id,
    current_sequence = current_sequence + 1,
    origin = 'RECORDED',
    observed_run_count = v_observed_run_count;

  INSERT INTO validation_run_registration(
    validation_run_id, label_version_id, rule_set_version_id, origin, registration_sequence
  )
  SELECT p_validation_run_id, p_label_version_id, p_rule_set_version_id,
         'RECORDED', current_sequence
  FROM validation_current_run
  WHERE label_version_id = p_label_version_id
    AND rule_set_version_id = p_rule_set_version_id;
END$$

DELIMITER ;

-- END VALIDATION PERSISTENCE V10

DROP PROCEDURE IF EXISTS sp_assert_current_validation_pass;
DROP PROCEDURE IF EXISTS sp_assert_current_label_version;
DROP PROCEDURE IF EXISTS sp_submit_label_for_review;
DROP PROCEDURE IF EXISTS sp_record_label_decision;
DROP PROCEDURE IF EXISTS sp_record_label_validation_pass;
DROP PROCEDURE IF EXISTS sp_record_label_validation_fail;

DELIMITER $$

CREATE PROCEDURE sp_assert_current_label_version(
  IN p_label_version_id VARCHAR(120)
)
BEGIN
  DECLARE v_product_id VARCHAR(100);
  DECLARE v_label_product_id VARCHAR(100);
  DECLARE v_formula_version_id VARCHAR(120);
  DECLARE v_current_formula_version_id VARCHAR(120);
  DECLARE v_jurisdiction_code VARCHAR(40);
  DECLARE v_version_number INT;
  DECLARE v_latest_version_number INT;

  -- Discover immutable identity without a label lock, then lock product first.
  SELECT product_id INTO v_product_id
  FROM label_version WHERE label_version_id = p_label_version_id;
  IF v_product_id IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Label version not found';
  END IF;

  SELECT current_formula_version_id INTO v_current_formula_version_id
  FROM product WHERE product_id = v_product_id FOR UPDATE;

  SELECT product_id, formula_version_id, jurisdiction_code, version_number
  INTO v_label_product_id, v_formula_version_id, v_jurisdiction_code, v_version_number
  FROM label_version WHERE label_version_id = p_label_version_id FOR UPDATE;

  IF v_label_product_id IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Label version not found';
  END IF;
  IF v_label_product_id <> v_product_id THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'LABEL_VERSION_CONFLICT: label product identity changed';
  END IF;

  SELECT version_number INTO v_latest_version_number
  FROM label_version
  WHERE product_id = v_product_id
    AND jurisdiction_code = v_jurisdiction_code
  ORDER BY version_number DESC
  LIMIT 1 FOR SHARE;

  IF v_current_formula_version_id IS NULL
     OR v_formula_version_id <> v_current_formula_version_id
     OR v_version_number <> v_latest_version_number THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'LABEL_VERSION_CONFLICT: label version is stale or historical';
  END IF;
END$$

-- Called inside the workflow's existing transaction/product lock. A locking
-- read observes a validation that committed while that lock was being waited on.
CREATE PROCEDURE sp_assert_current_validation_pass(
  IN p_label_version_id VARCHAR(120)
)
BEGIN
  DECLARE v_rule_set_version_id VARCHAR(100);
  DECLARE v_passed_validation_run_id VARCHAR(140);
  DECLARE v_observed_run_count BIGINT UNSIGNED;

  SELECT rule_set_version_id INTO v_rule_set_version_id
  FROM label_version WHERE label_version_id = p_label_version_id FOR SHARE;

  SELECT COUNT(*) INTO v_observed_run_count
  FROM validation_run FORCE INDEX (idx_validation_run_target_v10)
  WHERE label_version_id = p_label_version_id
    AND rule_set_version_id = v_rule_set_version_id
  FOR SHARE;

  SELECT vr.validation_run_id INTO v_passed_validation_run_id
  FROM validation_current_run current_run
  JOIN validation_run vr
    ON vr.validation_run_id = current_run.validation_run_id
   AND vr.label_version_id = current_run.label_version_id
   AND vr.rule_set_version_id = current_run.rule_set_version_id
  JOIN validation_run_registration registration
    ON registration.validation_run_id = vr.validation_run_id
   AND registration.label_version_id = vr.label_version_id
   AND registration.rule_set_version_id = vr.rule_set_version_id
  JOIN label_version lv
    ON lv.label_version_id = current_run.label_version_id
   AND lv.rule_set_version_id = current_run.rule_set_version_id
  WHERE current_run.label_version_id = p_label_version_id
    AND current_run.rule_set_version_id = v_rule_set_version_id
    AND current_run.observed_run_count = v_observed_run_count
    AND ((current_run.origin = 'LEGACY_UNIQUE' AND registration.origin = 'LEGACY')
      OR (current_run.origin = 'RECORDED' AND registration.origin = 'RECORDED'
        AND registration.registration_sequence = current_run.current_sequence))
    AND vr.status = 'PASSED'
  LIMIT 1 FOR SHARE;

  IF v_passed_validation_run_id IS NULL THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT =
        'LABEL_WORKFLOW_CONFLICT: current validation for the label and bound rule set must pass';
  END IF;
END$$

CREATE PROCEDURE sp_submit_label_for_review(
  IN p_label_version_id VARCHAR(120),
  IN p_actor_user_id VARCHAR(80)
)
BEGIN
  DECLARE v_permission_count INT DEFAULT 0;
  DECLARE v_returned_task_id VARCHAR(140);

  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  START TRANSACTION;

  CALL sp_assert_current_label_version(
    p_label_version_id
  );

  SELECT COUNT(*) INTO v_permission_count
  FROM user_account ua
  JOIN user_role ur
    ON ur.user_id = ua.user_id
  JOIN role_permission rp
    ON rp.role_id = ur.role_id
  JOIN permission p
    ON p.permission_id = rp.permission_id
  WHERE ua.user_id = p_actor_user_id
    AND ua.is_active = 'Y'
    AND p.permission_code = 'LABEL.SUBMIT_REVIEW';

  IF v_permission_count < 1 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT =
        'Actor lacks LABEL.SUBMIT_REVIEW permission';
  END IF;

  -- The Java command and direct helper require a fresh immutable revision.
  -- V4 decisions did not populate V7's decision summary: retain attributed
  -- approval history as a fallback without ordering same-second random IDs.
  SELECT rt.review_task_id INTO v_returned_task_id
  FROM review_task rt
  WHERE rt.target_label_version_id = p_label_version_id
    AND (rt.draft_label_version_id = p_label_version_id OR rt.draft_label_version_id IS NULL)
    AND rt.status = 'OPEN' AND rt.resolved_at IS NULL
    AND (rt.decision = 'REQUEST_CHANGES' OR EXISTS (
      SELECT 1 FROM approval_record ar
      WHERE ar.review_task_id = rt.review_task_id
        AND ar.label_version_id = p_label_version_id
        AND ar.decision = 'REQUEST_CHANGES'
      FOR SHARE
    ))
  LIMIT 1 FOR UPDATE;

  IF v_returned_task_id IS NOT NULL THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT =
        'LABEL_WORKFLOW_CONFLICT: REQUEST_CHANGES requires a new draft revision before resubmission';
  END IF;

  CALL sp_assert_current_validation_pass(p_label_version_id);

  UPDATE label_version
  SET lifecycle_status = 'PENDING_REVIEW'
  WHERE label_version_id = p_label_version_id
    AND lifecycle_status = 'DRAFT';

  IF ROW_COUNT() <> 1 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT =
        'Only DRAFT labels can move to PENDING_REVIEW';
  END IF;

  UPDATE review_task
  SET status = 'IN_REVIEW'
  WHERE draft_label_version_id = p_label_version_id
    AND status = 'OPEN';

  INSERT INTO audit_event (
    audit_event_id,
    event_type,
    entity_type,
    entity_id,
    event_at,
    actor_user_id,
    before_value,
    after_value,
    event_payload,
    correlation_id,
    data_provenance_id
  ) VALUES (
    CONCAT(
      'audit_label_submit_',
      REPLACE(UUID(), '-', '')
    ),
    'LABEL_PENDING_REVIEW',
    'LABEL_VERSION',
    p_label_version_id,
    NOW(),
    p_actor_user_id,
    JSON_OBJECT(
      'lifecycle_status',
      'DRAFT'
    ),
    JSON_OBJECT(
      'lifecycle_status',
      'PENDING_REVIEW'
    ),
    JSON_OBJECT(
      'helper',
      'sp_submit_label_for_review'
    ),
    p_label_version_id,
    'prov_validation_fixture'
  );

  COMMIT;
END$$

CREATE PROCEDURE sp_record_label_decision(
  IN p_label_version_id VARCHAR(120),
  IN p_actor_user_id VARCHAR(80),
  IN p_decision VARCHAR(40),
  IN p_comments VARCHAR(1000)
)
BEGIN
  DECLARE v_permission_count INT DEFAULT 0;
  DECLARE v_permission_code VARCHAR(140);
  DECLARE v_new_status VARCHAR(40);
  DECLARE v_creator_user_id VARCHAR(80);
  DECLARE v_review_task_id VARCHAR(140);

  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  IF p_decision = 'APPROVE' THEN
    SET v_permission_code = 'LABEL.APPROVE';
    SET v_new_status = 'APPROVED';
  ELSEIF p_decision = 'REQUEST_CHANGES' THEN
    SET v_permission_code =
      'LABEL.REQUEST_CHANGES';
    SET v_new_status = 'DRAFT';
  ELSEIF p_decision = 'REJECT' THEN
    SET v_permission_code = 'LABEL.REJECT';
    SET v_new_status = 'REJECTED';
  ELSE
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT =
        'Unsupported label decision';
  END IF;

  START TRANSACTION;

  CALL sp_assert_current_label_version(
    p_label_version_id
  );

  SELECT created_by_user_id
  INTO v_creator_user_id
  FROM label_version
  WHERE label_version_id = p_label_version_id;

  SELECT COUNT(*) INTO v_permission_count
  FROM user_account ua
  JOIN user_role ur
    ON ur.user_id = ua.user_id
  JOIN role_permission rp
    ON rp.role_id = ur.role_id
  JOIN permission p
    ON p.permission_id = rp.permission_id
  WHERE ua.user_id = p_actor_user_id
    AND ua.is_active = 'Y'
    AND p.permission_code = v_permission_code;

  IF v_permission_count < 1 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT =
        'Actor lacks required label-decision permission';
  END IF;

  SELECT rt.review_task_id
  INTO v_review_task_id
  FROM review_task rt
  WHERE rt.draft_label_version_id =
        p_label_version_id
    AND EXISTS (
      SELECT 1
      FROM label_version lv
      WHERE lv.label_version_id =
            p_label_version_id
        AND lv.lifecycle_status =
            'PENDING_REVIEW'
    )
  LIMIT 1;

  IF v_review_task_id IS NULL THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT =
        'Decision requires a pending-review label and review task';
  END IF;

  IF v_creator_user_id = p_actor_user_id THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT =
        'Maker-checker violation: label creator cannot approve own label';
  END IF;

  IF p_decision = 'APPROVE' THEN
    CALL sp_assert_current_validation_pass(p_label_version_id);
  END IF;

  UPDATE label_version
  SET lifecycle_status = v_new_status
  WHERE label_version_id = p_label_version_id
    AND lifecycle_status = 'PENDING_REVIEW';

  IF ROW_COUNT() <> 1 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT =
        'Label decision state transition failed';
  END IF;

  IF p_decision IN ('APPROVE', 'REJECT') THEN
    UPDATE review_task
    SET status = 'CLOSED'
    WHERE review_task_id = v_review_task_id;
  ELSE
    UPDATE review_task
    SET status = 'OPEN'
    WHERE review_task_id = v_review_task_id;
  END IF;

  INSERT INTO approval_record (
    approval_record_id,
    label_version_id,
    review_task_id,
    decision,
    decided_by_user_id,
    decided_at,
    comments,
    data_provenance_id
  ) VALUES (
    CONCAT(
      'decision_',
      REPLACE(UUID(), '-', '')
    ),
    p_label_version_id,
    v_review_task_id,
    p_decision,
    p_actor_user_id,
    NOW(),
    p_comments,
    'prov_validation_fixture'
  );

  INSERT INTO audit_event (
    audit_event_id,
    event_type,
    entity_type,
    entity_id,
    event_at,
    actor_user_id,
    before_value,
    after_value,
    event_payload,
    correlation_id,
    data_provenance_id
  ) VALUES (
    CONCAT(
      'audit_label_decision_',
      REPLACE(UUID(), '-', '')
    ),
    'LABEL_DECISION_RECORDED',
    'LABEL_VERSION',
    p_label_version_id,
    NOW(),
    p_actor_user_id,
    JSON_OBJECT(
      'lifecycle_status',
      'PENDING_REVIEW'
    ),
    JSON_OBJECT(
      'lifecycle_status',
      v_new_status
    ),
    JSON_OBJECT(
      'decision',
      p_decision,
      'helper',
      'sp_record_label_decision'
    ),
    p_label_version_id,
    'prov_validation_fixture'
  );

  COMMIT;
END$$

CREATE PROCEDURE sp_record_label_validation_pass(
  IN p_label_version_id VARCHAR(120),
  IN p_actor_user_id VARCHAR(80)
)
BEGIN
  DECLARE v_permission_count INT DEFAULT 0;
  DECLARE v_rule_set_version_id VARCHAR(100);
  DECLARE v_validation_run_id VARCHAR(140);
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  SELECT COUNT(*) INTO v_permission_count
  FROM user_account ua
  JOIN user_role ur ON ur.user_id = ua.user_id
  JOIN role_permission rp ON rp.role_id = ur.role_id
  JOIN permission p ON p.permission_id = rp.permission_id
  WHERE ua.user_id = p_actor_user_id
    AND ua.is_active = 'Y'
    AND p.permission_code = 'LABEL.VALIDATE';

  IF v_permission_count < 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Actor lacks LABEL.VALIDATE permission';
  END IF;

  SELECT rule_set_version_id INTO v_rule_set_version_id
  FROM label_version
  WHERE label_version_id = p_label_version_id;

  IF v_rule_set_version_id IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Label version does not exist';
  END IF;

  SET v_validation_run_id = CONCAT('val_pass_', p_label_version_id);

  START TRANSACTION;
  CALL sp_insert_validation_run(
    v_validation_run_id, p_label_version_id, v_rule_set_version_id, 'PASSED',
    p_actor_user_id, NOW(), 'Fixture validation passed: structured label declarations match formula-derived allergens.', 'prov_validation_fixture'
  );

  INSERT INTO validation_result (
    validation_result_id, validation_run_id, rule_definition_id, result_code,
    severity, passed, blocking, message
  ) VALUES (
    CONCAT('valres_pass_', p_label_version_id), v_validation_run_id, NULL,
    'FORMULA_LABEL_ALLERGEN_MATCH', 'INFO', 'Y', 'N',
    'SOY/MILK/WHEAT controlled fixture allergens match the structured label declaration.'
  );

  INSERT INTO audit_event (
    audit_event_id, event_type, entity_type, entity_id, event_at, actor_user_id,
    before_value, after_value, event_payload, correlation_id, data_provenance_id
  ) VALUES (
    CONCAT('audit_val_pass_', p_label_version_id), 'LABEL_VALIDATION_PASSED',
    'LABEL_VERSION', p_label_version_id, NOW(), p_actor_user_id,
    NULL, JSON_OBJECT('validation_status','PASSED'), JSON_OBJECT('helper','sp_record_label_validation_pass'),
    p_label_version_id, 'prov_validation_fixture'
  );
  COMMIT;
END$$

CREATE PROCEDURE sp_record_label_validation_fail(
  IN p_label_version_id VARCHAR(120),
  IN p_actor_user_id VARCHAR(80),
  IN p_message VARCHAR(1000)
)
BEGIN
  DECLARE v_permission_count INT DEFAULT 0;
  DECLARE v_rule_set_version_id VARCHAR(100);
  DECLARE v_validation_run_id VARCHAR(140);
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  SELECT COUNT(*) INTO v_permission_count
  FROM user_account ua
  JOIN user_role ur ON ur.user_id = ua.user_id
  JOIN role_permission rp ON rp.role_id = ur.role_id
  JOIN permission p ON p.permission_id = rp.permission_id
  WHERE ua.user_id = p_actor_user_id
    AND ua.is_active = 'Y'
    AND p.permission_code = 'LABEL.VALIDATE';

  IF v_permission_count < 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Actor lacks LABEL.VALIDATE permission';
  END IF;

  SELECT rule_set_version_id INTO v_rule_set_version_id
  FROM label_version
  WHERE label_version_id = p_label_version_id;

  IF v_rule_set_version_id IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Label version does not exist';
  END IF;

  SET v_validation_run_id = CONCAT('val_fail_', p_label_version_id);

  START TRANSACTION;
  CALL sp_insert_validation_run(
    v_validation_run_id, p_label_version_id, v_rule_set_version_id, 'FAILED',
    p_actor_user_id, NOW(), p_message, 'prov_validation_fixture'
  );

  INSERT INTO validation_result (
    validation_result_id, validation_run_id, rule_definition_id, result_code,
    severity, passed, blocking, message
  ) VALUES (
    CONCAT('valres_fail_', p_label_version_id), v_validation_run_id, NULL,
    'FORMULA_LABEL_ALLERGEN_MISMATCH', 'ERROR', 'N', 'Y', p_message
  );

  INSERT INTO audit_event (
    audit_event_id, event_type, entity_type, entity_id, event_at, actor_user_id,
    before_value, after_value, event_payload, correlation_id, data_provenance_id
  ) VALUES (
    CONCAT('audit_val_fail_', p_label_version_id), 'LABEL_VALIDATION_FAILED',
    'LABEL_VERSION', p_label_version_id, NOW(), p_actor_user_id,
    NULL, JSON_OBJECT('validation_status','FAILED'), JSON_OBJECT('helper','sp_record_label_validation_fail'),
    p_label_version_id, 'prov_validation_fixture'
  );
  COMMIT;
END$$

DELIMITER ;
