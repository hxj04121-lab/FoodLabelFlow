-- Forward-only REQUEST_CHANGES resubmission guard. V1-V8 and historical
-- target('6') upgrade fixtures remain unchanged. No business rows are updated.
DROP PROCEDURE IF EXISTS sp_submit_label_for_review;

DELIMITER $$

CREATE PROCEDURE sp_submit_label_for_review(
  IN p_label_version_id VARCHAR(120),
  IN p_actor_user_id VARCHAR(80)
)
BEGIN
  DECLARE v_permission_count INT DEFAULT 0;
  DECLARE v_passed_validation_count INT DEFAULT 0;
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

DELIMITER ;
