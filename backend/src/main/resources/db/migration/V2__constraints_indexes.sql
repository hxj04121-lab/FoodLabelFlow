SET NAMES utf8mb4;
USE spectrace;

ALTER TABLE product
  ADD CONSTRAINT fk_product_current_formula_v3
    FOREIGN KEY (current_formula_version_id) REFERENCES formula_version(formula_version_id),
  ADD CONSTRAINT fk_product_current_label_v3
    FOREIGN KEY (current_published_label_version_id) REFERENCES label_version(label_version_id);

ALTER TABLE formula_version
  ADD COLUMN current_formula_product_id VARCHAR(100)
    GENERATED ALWAYS AS (
      CASE WHEN lifecycle_status = 'RELEASED' AND is_current_released = 'Y' THEN product_id ELSE NULL END
    ) STORED,
  ADD UNIQUE KEY uq_formula_one_current_released_per_product_v3 (current_formula_product_id);

ALTER TABLE label_version
  ADD COLUMN current_published_label_scope_key VARCHAR(160)
    GENERATED ALWAYS AS (
      CASE
        WHEN lifecycle_status = 'PUBLISHED' AND is_current_published = 'Y'
        THEN CONCAT(product_id, '|', jurisdiction_code)
        ELSE NULL
      END
    ) STORED,
  ADD UNIQUE KEY uq_label_one_current_published_per_product_jurisdiction_v3 (current_published_label_scope_key);

ALTER TABLE data_provenance
  ADD CONSTRAINT chk_data_provenance_source_type_v3
    CHECK (source_type IN ('PUBLIC_SOURCE','PROJECT_SEEDED','DERIVED','SYSTEM_GENERATED','VALIDATION_OUTPUT'));

ALTER TABLE user_account
  ADD CONSTRAINT chk_user_active_flag_v3 CHECK (is_active IN ('Y','N')),
  ADD CONSTRAINT chk_user_auth_mode_v3 CHECK (
    (auth_provider = 'DEV_EXTERNAL' AND external_auth_subject IS NOT NULL AND password_hash IS NULL)
    OR
    (auth_provider = 'DEV_HASHED' AND password_hash IS NOT NULL)
  );

ALTER TABLE ingredient
  ADD CONSTRAINT chk_ingredient_source_type_v3
    CHECK (source_type IN ('PUBLIC_SOURCE','PROJECT_SEEDED','DERIVED','SYSTEM_GENERATED')),
  ADD CONSTRAINT chk_ingredient_kind_v3
    CHECK (ingredient_kind IN ('CANONICAL','COMPOUND','PLACEHOLDER'));

ALTER TABLE rule_set_version
  ADD CONSTRAINT chk_rule_set_lifecycle_v3 CHECK (lifecycle_status IN ('DRAFT','ACTIVE','RETIRED')),
  ADD CONSTRAINT chk_rule_set_demo_flag_v3 CHECK (is_demo_only IN ('Y','N'));

ALTER TABLE rule_definition
  ADD CONSTRAINT chk_rule_definition_type_v3 CHECK (rule_type IN ('INGREDIENT_TO_ALLERGEN','LABEL_DECLARATION_VALIDATION')),
  ADD CONSTRAINT chk_rule_definition_severity_v3 CHECK (severity IN ('INFO','WARNING','ERROR')),
  ADD CONSTRAINT chk_rule_definition_active_flag_v3 CHECK (is_active IN ('Y','N'));

ALTER TABLE ingredient_specification_version
  ADD CONSTRAINT chk_spec_lifecycle_v3 CHECK (lifecycle_status IN ('DRAFT','RELEASED','RETIRED'));

ALTER TABLE spec_component
  ADD CONSTRAINT chk_spec_component_match_status_v3 CHECK (match_status IN ('MATCHED','UNMAPPED','AMBIGUOUS'));

ALTER TABLE product
  ADD CONSTRAINT chk_product_source_type_v3 CHECK (source_type IN ('PUBLIC_SOURCE','PROJECT_SEEDED','DERIVED')),
  ADD CONSTRAINT chk_product_fixture_group_v3 CHECK (fixture_group IN (
    'NO_ACTION_BASELINE_SOY',
    'REVIEW_REQUIRED_BASELINE_NO_SOY',
    'NEGATIVE_CONTROL_NO_CHOCOLATE'
  ));

ALTER TABLE formula_version
  ADD CONSTRAINT chk_formula_lifecycle_v3 CHECK (lifecycle_status IN ('DRAFT','ANALYSIS','RELEASED','RETIRED')),
  ADD CONSTRAINT chk_formula_current_flag_v3 CHECK (is_current_released IN ('Y','N'));

ALTER TABLE label_version
  ADD CONSTRAINT chk_label_lifecycle_v3 CHECK (lifecycle_status IN (
    'DRAFT','PENDING_REVIEW','APPROVED','PUBLISHED','SUPERSEDED','REJECTED'
  )),
  ADD CONSTRAINT chk_label_current_flag_v3 CHECK (is_current_published IN ('Y','N'));

ALTER TABLE label_allergen_declaration
  ADD CONSTRAINT chk_label_declaration_type_v3 CHECK (declaration_type IN ('CONTAINS')),
  ADD CONSTRAINT chk_label_declaration_source_v3 CHECK (declaration_source IN (
    'MIGRATED_PUBLIC_LABEL','FORMULA_DERIVED','SYSTEM_PROPOSED','USER_ENTERED'
  ));

ALTER TABLE validation_run
  ADD CONSTRAINT chk_validation_run_status_v3 CHECK (status IN ('PASSED','FAILED'));

ALTER TABLE validation_result
  ADD CONSTRAINT chk_validation_result_severity_v3 CHECK (severity IN ('INFO','WARNING','ERROR')),
  ADD CONSTRAINT chk_validation_result_passed_flag_v3 CHECK (passed IN ('Y','N')),
  ADD CONSTRAINT chk_validation_result_blocking_flag_v3 CHECK (blocking IN ('Y','N'));

ALTER TABLE change_request
  ADD CONSTRAINT chk_change_request_type_v3 CHECK (change_type IN ('INGREDIENT_SPEC','FORMULA','RULE_SET')),
  ADD CONSTRAINT chk_change_request_status_v3 CHECK (status IN ('DRAFT','SUBMITTED','ANALYZED','COMPLETED','CANCELLED')),
  ADD CONSTRAINT chk_change_request_typed_refs_v3 CHECK (
    (
      change_type = 'INGREDIENT_SPEC'
      AND from_specification_version_id IS NOT NULL
      AND to_specification_version_id IS NOT NULL
      AND from_formula_version_id IS NULL
      AND to_formula_version_id IS NULL
      AND from_rule_set_version_id IS NULL
      AND to_rule_set_version_id IS NULL
    )
    OR
    (
      change_type = 'FORMULA'
      AND from_formula_version_id IS NOT NULL
      AND to_formula_version_id IS NOT NULL
      AND from_specification_version_id IS NULL
      AND to_specification_version_id IS NULL
      AND from_rule_set_version_id IS NULL
      AND to_rule_set_version_id IS NULL
    )
    OR
    (
      change_type = 'RULE_SET'
      AND from_rule_set_version_id IS NOT NULL
      AND to_rule_set_version_id IS NOT NULL
      AND from_specification_version_id IS NULL
      AND to_specification_version_id IS NULL
      AND from_formula_version_id IS NULL
      AND to_formula_version_id IS NULL
    )
  );

ALTER TABLE impact_analysis_run
  ADD CONSTRAINT chk_impact_run_status_v3 CHECK (status IN ('QUEUED','RUNNING','COMPLETED','FAILED'));

ALTER TABLE impact_finding
  ADD CONSTRAINT chk_impact_finding_classification_v3 CHECK (classification IN ('NO_ACTION','REVIEW_REQUIRED'));

ALTER TABLE review_task
  ADD CONSTRAINT chk_review_task_status_v3 CHECK (status IN ('OPEN','IN_REVIEW','CLOSED'));

ALTER TABLE approval_record
  ADD CONSTRAINT chk_approval_decision_v3 CHECK (decision IN ('REQUEST_CHANGES','REJECT','APPROVE'));

CREATE INDEX idx_supplier_material_ingredient_v3 ON supplier_material(ingredient_id);
CREATE INDEX idx_spec_supplier_status_v3 ON ingredient_specification_version(supplier_material_id, lifecycle_status, version_number);
CREATE INDEX idx_spec_component_allergen_path_v3 ON spec_component(specification_version_id, ingredient_id);
CREATE INDEX idx_ingredient_allergen_rule_v3 ON ingredient_allergen(rule_set_version_id, ingredient_id, allergen_id);
CREATE INDEX idx_product_fixture_group_v3 ON product(fixture_group);
CREATE INDEX idx_product_current_formula_v3 ON product(current_formula_version_id);
CREATE INDEX idx_formula_product_status_v3 ON formula_version(product_id, lifecycle_status, is_current_released);
CREATE INDEX idx_formula_item_runtime_impact_v3 ON formula_item(supplier_material_id, specification_version_id, formula_version_id);
CREATE INDEX idx_label_product_status_v3 ON label_version(product_id, jurisdiction_code, lifecycle_status, is_current_published);
CREATE INDEX idx_label_declaration_lookup_v3 ON label_allergen_declaration(label_version_id, allergen_id, declaration_type);
CREATE INDEX idx_change_request_type_status_v3 ON change_request(change_type, status);
CREATE INDEX idx_impact_run_change_status_v3 ON impact_analysis_run(change_request_id, status);
CREATE INDEX idx_impact_finding_class_v3 ON impact_finding(impact_analysis_run_id, classification, product_id);
CREATE INDEX idx_review_task_status_v3 ON review_task(status, assigned_to_user_id);
CREATE INDEX idx_audit_event_entity_v3 ON audit_event(entity_type, entity_id, event_at);

DROP PROCEDURE IF EXISTS sp_record_label_validation_pass;
DROP PROCEDURE IF EXISTS sp_record_label_validation_fail;
DROP PROCEDURE IF EXISTS sp_submit_label_for_review;
DROP PROCEDURE IF EXISTS sp_record_label_decision;
DROP PROCEDURE IF EXISTS sp_publish_label;
DROP PROCEDURE IF EXISTS sp_release_formula_version;

DELIMITER $$

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
  INSERT INTO validation_run (
    validation_run_id, label_version_id, rule_set_version_id, status,
    ran_by_user_id, ran_at, summary, data_provenance_id
  ) VALUES (
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
  INSERT INTO validation_run (
    validation_run_id, label_version_id, rule_set_version_id, status,
    ran_by_user_id, ran_at, summary, data_provenance_id
  ) VALUES (
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

CREATE PROCEDURE sp_release_formula_version(
  IN p_formula_version_id VARCHAR(120),
  IN p_actor_user_id VARCHAR(80)
)
BEGIN
  DECLARE v_permission_count INT DEFAULT 0;
  DECLARE v_product_id VARCHAR(100);
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
    AND p.permission_code = 'FORMULA.RELEASE';

  IF v_permission_count < 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Actor lacks FORMULA.RELEASE permission';
  END IF;

  SELECT product_id INTO v_product_id
  FROM formula_version
  WHERE formula_version_id = p_formula_version_id
    AND lifecycle_status IN ('DRAFT','ANALYSIS');

  IF v_product_id IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Formula must exist and be DRAFT/ANALYSIS before release';
  END IF;

  START TRANSACTION;
  UPDATE formula_version
  SET is_current_released = 'N'
  WHERE product_id = v_product_id
    AND is_current_released = 'Y';

  UPDATE formula_version
  SET lifecycle_status = 'RELEASED',
      is_current_released = 'Y',
      released_by_user_id = p_actor_user_id,
      released_at = NOW()
  WHERE formula_version_id = p_formula_version_id
    AND lifecycle_status IN ('DRAFT','ANALYSIS');

  IF ROW_COUNT() <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Formula release state transition failed';
  END IF;

  UPDATE product
  SET current_formula_version_id = p_formula_version_id
  WHERE product_id = v_product_id;

  INSERT INTO audit_event (
    audit_event_id, event_type, entity_type, entity_id, event_at, actor_user_id,
    before_value, after_value, event_payload, correlation_id, data_provenance_id
  ) VALUES (
    CONCAT('audit_formula_release_', REPLACE(UUID(),'-','')), 'FORMULA_RELEASED',
    'FORMULA_VERSION', p_formula_version_id, NOW(), p_actor_user_id,
    NULL, JSON_OBJECT('lifecycle_status','RELEASED','is_current_released','Y'),
    JSON_OBJECT('helper','sp_release_formula_version'), p_formula_version_id, 'prov_validation_fixture'
  );
  COMMIT;
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

  SELECT COUNT(*) INTO v_permission_count
  FROM user_account ua
  JOIN user_role ur ON ur.user_id = ua.user_id
  JOIN role_permission rp ON rp.role_id = ur.role_id
  JOIN permission p ON p.permission_id = rp.permission_id
  WHERE ua.user_id = p_actor_user_id
    AND ua.is_active = 'Y'
    AND p.permission_code = 'LABEL.SUBMIT_REVIEW';

  IF v_permission_count < 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Actor lacks LABEL.SUBMIT_REVIEW permission';
  END IF;

  SELECT COUNT(*) INTO v_passed_validation_count
  FROM validation_run
  WHERE label_version_id = p_label_version_id
    AND status = 'PASSED';

  IF v_passed_validation_count < 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Passed validation is required before pending review';
  END IF;

  START TRANSACTION;
  UPDATE label_version
  SET lifecycle_status = 'PENDING_REVIEW'
  WHERE label_version_id = p_label_version_id
    AND lifecycle_status = 'DRAFT';

  IF ROW_COUNT() <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Only DRAFT labels can move to PENDING_REVIEW';
  END IF;

  UPDATE review_task
  SET status = 'IN_REVIEW'
  WHERE draft_label_version_id = p_label_version_id
    AND status = 'OPEN';

  INSERT INTO audit_event (
    audit_event_id, event_type, entity_type, entity_id, event_at, actor_user_id,
    before_value, after_value, event_payload, correlation_id, data_provenance_id
  ) VALUES (
    CONCAT('audit_label_submit_', REPLACE(UUID(),'-','')), 'LABEL_PENDING_REVIEW',
    'LABEL_VERSION', p_label_version_id, NOW(), p_actor_user_id,
    JSON_OBJECT('lifecycle_status','DRAFT'), JSON_OBJECT('lifecycle_status','PENDING_REVIEW'),
    JSON_OBJECT('helper','sp_submit_label_for_review'), p_label_version_id, 'prov_validation_fixture'
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
    SET v_permission_code = 'LABEL.REQUEST_CHANGES';
    SET v_new_status = 'DRAFT';
  ELSEIF p_decision = 'REJECT' THEN
    SET v_permission_code = 'LABEL.REJECT';
    SET v_new_status = 'REJECTED';
  ELSE
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Unsupported label decision';
  END IF;

  SELECT COUNT(*) INTO v_permission_count
  FROM user_account ua
  JOIN user_role ur ON ur.user_id = ua.user_id
  JOIN role_permission rp ON rp.role_id = ur.role_id
  JOIN permission p ON p.permission_id = rp.permission_id
  WHERE ua.user_id = p_actor_user_id
    AND ua.is_active = 'Y'
    AND p.permission_code = v_permission_code;

  IF v_permission_count < 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Actor lacks required label-decision permission';
  END IF;

  SELECT lv.created_by_user_id, rt.review_task_id
  INTO v_creator_user_id, v_review_task_id
  FROM label_version lv
  JOIN review_task rt ON rt.draft_label_version_id = lv.label_version_id
  WHERE lv.label_version_id = p_label_version_id
    AND lv.lifecycle_status = 'PENDING_REVIEW'
  LIMIT 1;

  IF v_review_task_id IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Decision requires a pending-review label and review task';
  END IF;

  IF v_creator_user_id = p_actor_user_id THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Maker-checker violation: label creator cannot approve own label';
  END IF;

  START TRANSACTION;
  UPDATE label_version
  SET lifecycle_status = v_new_status
  WHERE label_version_id = p_label_version_id
    AND lifecycle_status = 'PENDING_REVIEW';

  IF ROW_COUNT() <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Label decision state transition failed';
  END IF;

  IF p_decision IN ('APPROVE','REJECT') THEN
    UPDATE review_task
    SET status = 'CLOSED'
    WHERE review_task_id = v_review_task_id;
  ELSE
    UPDATE review_task
    SET status = 'OPEN'
    WHERE review_task_id = v_review_task_id;
  END IF;

  INSERT INTO approval_record (
    approval_record_id, label_version_id, review_task_id, decision,
    decided_by_user_id, decided_at, comments, data_provenance_id
  ) VALUES (
    CONCAT('decision_', REPLACE(UUID(),'-','')), p_label_version_id, v_review_task_id,
    p_decision, p_actor_user_id, NOW(), p_comments, 'prov_validation_fixture'
  );

  INSERT INTO audit_event (
    audit_event_id, event_type, entity_type, entity_id, event_at, actor_user_id,
    before_value, after_value, event_payload, correlation_id, data_provenance_id
  ) VALUES (
    CONCAT('audit_label_decision_', REPLACE(UUID(),'-','')), 'LABEL_DECISION_RECORDED',
    'LABEL_VERSION', p_label_version_id, NOW(), p_actor_user_id,
    JSON_OBJECT('lifecycle_status','PENDING_REVIEW'), JSON_OBJECT('lifecycle_status',v_new_status),
    JSON_OBJECT('decision',p_decision,'helper','sp_record_label_decision'),
    p_label_version_id, 'prov_validation_fixture'
  );
  COMMIT;
END$$

CREATE PROCEDURE sp_publish_label(
  IN p_label_version_id VARCHAR(120),
  IN p_actor_user_id VARCHAR(80)
)
BEGIN
  DECLARE v_permission_count INT DEFAULT 0;
  DECLARE v_product_id VARCHAR(100);
  DECLARE v_formula_version_id VARCHAR(120);
  DECLARE v_jurisdiction_code VARCHAR(40);
  DECLARE v_current_formula_version_id VARCHAR(120);
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
    AND p.permission_code = 'LABEL.PUBLISH';

  IF v_permission_count < 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Actor lacks LABEL.PUBLISH permission';
  END IF;

  SELECT lv.product_id, lv.formula_version_id, lv.jurisdiction_code, p.current_formula_version_id
  INTO v_product_id, v_formula_version_id, v_jurisdiction_code, v_current_formula_version_id
  FROM label_version lv
  JOIN product p ON p.product_id = lv.product_id
  WHERE lv.label_version_id = p_label_version_id
    AND lv.lifecycle_status = 'APPROVED';

  IF v_product_id IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Label must be APPROVED before publication';
  END IF;

  IF v_formula_version_id <> v_current_formula_version_id THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Label formula must already be the product current formula before publication';
  END IF;

  START TRANSACTION;
  UPDATE label_version
  SET lifecycle_status = 'SUPERSEDED',
      is_current_published = 'N'
  WHERE product_id = v_product_id
    AND jurisdiction_code = v_jurisdiction_code
    AND lifecycle_status = 'PUBLISHED'
    AND is_current_published = 'Y';

  UPDATE label_version
  SET lifecycle_status = 'PUBLISHED',
      is_current_published = 'Y'
  WHERE label_version_id = p_label_version_id
    AND lifecycle_status = 'APPROVED';

  IF ROW_COUNT() <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Publication state transition failed';
  END IF;

  UPDATE product
  SET current_published_label_version_id = p_label_version_id
  WHERE product_id = v_product_id;

  INSERT INTO publication_record (
    publication_record_id, label_version_id, published_by_user_id,
    published_at, publication_channel, data_provenance_id
  ) VALUES (
    CONCAT('publication_', p_label_version_id), p_label_version_id, p_actor_user_id,
    NOW(), 'DEMO_RELEASE', 'prov_validation_fixture'
  );

  INSERT INTO audit_event (
    audit_event_id, event_type, entity_type, entity_id, event_at, actor_user_id,
    before_value, after_value, event_payload, correlation_id, data_provenance_id
  ) VALUES (
    CONCAT('audit_label_publish_', REPLACE(UUID(),'-','')), 'LABEL_PUBLISHED',
    'LABEL_VERSION', p_label_version_id, NOW(), p_actor_user_id,
    JSON_OBJECT('lifecycle_status','APPROVED','is_current_published','N'),
    JSON_OBJECT('lifecycle_status','PUBLISHED','is_current_published','Y'),
    JSON_OBJECT('helper','sp_publish_label'), p_label_version_id, 'prov_validation_fixture'
  );
  COMMIT;
END$$

DELIMITER ;
