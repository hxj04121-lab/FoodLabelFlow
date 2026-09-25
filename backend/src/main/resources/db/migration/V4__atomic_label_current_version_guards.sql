DROP PROCEDURE IF EXISTS sp_assert_current_label_version;
DROP PROCEDURE IF EXISTS sp_submit_label_for_review;
DROP PROCEDURE IF EXISTS sp_record_label_decision;

DELIMITER $$

CREATE PROCEDURE sp_assert_current_label_version(
  IN p_label_version_id VARCHAR(120)
)
BEGIN
  DECLARE v_product_id VARCHAR(100);
  DECLARE v_formula_version_id VARCHAR(120);
  DECLARE v_current_formula_version_id VARCHAR(120);
  DECLARE v_jurisdiction_code VARCHAR(40);
  DECLARE v_version_number INT;
  DECLARE v_latest_version_number INT;

  /*
   * The product row is the serialization point shared with draft
   * creation and current-formula pointer changes.
   *
   * This helper must be called from an existing transaction.
   */
  SELECT
      lv.product_id,
      lv.formula_version_id,
      p.current_formula_version_id,
      lv.jurisdiction_code,
      lv.version_number
  INTO
      v_product_id,
      v_formula_version_id,
      v_current_formula_version_id,
      v_jurisdiction_code,
      v_version_number
  FROM label_version lv
  JOIN product p
    ON p.product_id = lv.product_id
  WHERE lv.label_version_id = p_label_version_id
  FOR UPDATE;

  IF v_product_id IS NULL THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Label version not found';
  END IF;

  SELECT MAX(version_number)
  INTO v_latest_version_number
  FROM label_version
  WHERE product_id = v_product_id
    AND jurisdiction_code = v_jurisdiction_code;

  IF v_formula_version_id <> v_current_formula_version_id
     OR v_version_number <> v_latest_version_number THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT =
        'LABEL_VERSION_CONFLICT: label version is stale or historical';
  END IF;
END$$


CREATE PROCEDURE sp_submit_label_for_review(
  IN p_label_version_id VARCHAR(120),
  IN p_actor_user_id VARCHAR(80)
)
BEGIN
  DECLARE v_permission_count INT DEFAULT 0;
  DECLARE v_passed_validation_count INT DEFAULT 0;

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

  SELECT COUNT(*) INTO v_passed_validation_count
  FROM validation_run
  WHERE label_version_id = p_label_version_id
    AND status = 'PASSED';

  IF v_passed_validation_count < 1 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT =
        'Passed validation is required before pending review';
  END IF;

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

DELIMITER ;