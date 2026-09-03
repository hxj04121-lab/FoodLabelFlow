SET NAMES utf8mb4;
CREATE DATABASE IF NOT EXISTS spectrace CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE spectrace;

CREATE TABLE IF NOT EXISTS data_provenance (
  provenance_id VARCHAR(100) PRIMARY KEY,
  source_type VARCHAR(30) NOT NULL,
  source_dataset VARCHAR(200),
  source_record_id VARCHAR(160),
  source_url VARCHAR(600),
  source_snapshot_date DATE,
  derivation_rule VARCHAR(800),
  synthetic_reason VARCHAR(800),
  created_at DATETIME NOT NULL,
  created_by VARCHAR(120) NOT NULL
);

CREATE TABLE IF NOT EXISTS user_account (
  user_id VARCHAR(80) PRIMARY KEY,
  username VARCHAR(120) NOT NULL UNIQUE,
  display_name VARCHAR(160) NOT NULL,
  email VARCHAR(200),
  auth_provider VARCHAR(80) NOT NULL,
  external_auth_subject VARCHAR(200),
  password_hash VARCHAR(255),
  is_active CHAR(1) NOT NULL DEFAULT 'Y',
  created_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS `role` (
  role_id VARCHAR(80) PRIMARY KEY,
  role_code VARCHAR(100) NOT NULL UNIQUE,
  role_name VARCHAR(160) NOT NULL,
  description VARCHAR(600)
);

CREATE TABLE IF NOT EXISTS permission (
  permission_id VARCHAR(100) PRIMARY KEY,
  permission_code VARCHAR(140) NOT NULL UNIQUE,
  permission_name VARCHAR(180) NOT NULL,
  description VARCHAR(600)
);

CREATE TABLE IF NOT EXISTS user_role (
  user_id VARCHAR(80) NOT NULL,
  role_id VARCHAR(80) NOT NULL,
  assigned_at DATETIME NOT NULL,
  PRIMARY KEY (user_id, role_id),
  FOREIGN KEY (user_id) REFERENCES user_account(user_id),
  FOREIGN KEY (role_id) REFERENCES `role`(role_id)
);

CREATE TABLE IF NOT EXISTS role_permission (
  role_id VARCHAR(80) NOT NULL,
  permission_id VARCHAR(100) NOT NULL,
  PRIMARY KEY (role_id, permission_id),
  FOREIGN KEY (role_id) REFERENCES `role`(role_id),
  FOREIGN KEY (permission_id) REFERENCES permission(permission_id)
);

CREATE TABLE IF NOT EXISTS supplier (
  supplier_id VARCHAR(80) PRIMARY KEY,
  supplier_code VARCHAR(80) NOT NULL UNIQUE,
  supplier_name VARCHAR(200) NOT NULL,
  data_provenance_id VARCHAR(100) NOT NULL,
  FOREIGN KEY (data_provenance_id) REFERENCES data_provenance(provenance_id)
);

CREATE TABLE IF NOT EXISTS ingredient (
  ingredient_id VARCHAR(80) PRIMARY KEY,
  canonical_name VARCHAR(200) NOT NULL UNIQUE,
  ingredient_kind VARCHAR(60) NOT NULL,
  source_type VARCHAR(30) NOT NULL,
  data_provenance_id VARCHAR(100) NOT NULL,
  FOREIGN KEY (data_provenance_id) REFERENCES data_provenance(provenance_id)
);

CREATE TABLE IF NOT EXISTS supplier_material (
  supplier_material_id VARCHAR(80) PRIMARY KEY,
  supplier_id VARCHAR(80) NOT NULL,
  ingredient_id VARCHAR(80),
  material_code VARCHAR(100) NOT NULL,
  material_name VARCHAR(200) NOT NULL,
  material_description VARCHAR(600),
  data_provenance_id VARCHAR(100) NOT NULL,
  UNIQUE (supplier_id, material_code),
  FOREIGN KEY (supplier_id) REFERENCES supplier(supplier_id),
  FOREIGN KEY (ingredient_id) REFERENCES ingredient(ingredient_id),
  FOREIGN KEY (data_provenance_id) REFERENCES data_provenance(provenance_id)
);

CREATE TABLE IF NOT EXISTS allergen (
  allergen_id VARCHAR(80) PRIMARY KEY,
  allergen_code VARCHAR(50) NOT NULL UNIQUE,
  display_name VARCHAR(120) NOT NULL,
  jurisdiction_code VARCHAR(40) NOT NULL,
  data_provenance_id VARCHAR(100) NOT NULL,
  FOREIGN KEY (data_provenance_id) REFERENCES data_provenance(provenance_id)
);

CREATE TABLE IF NOT EXISTS rule_set_version (
  rule_set_version_id VARCHAR(100) PRIMARY KEY,
  rule_set_code VARCHAR(100) NOT NULL,
  version_number VARCHAR(40) NOT NULL,
  jurisdiction_code VARCHAR(40) NOT NULL,
  lifecycle_status VARCHAR(40) NOT NULL,
  effective_from DATE NOT NULL,
  effective_to DATE,
  is_demo_only CHAR(1) NOT NULL,
  description VARCHAR(800) NOT NULL,
  data_provenance_id VARCHAR(100) NOT NULL,
  UNIQUE (rule_set_code, version_number),
  FOREIGN KEY (data_provenance_id) REFERENCES data_provenance(provenance_id)
);

CREATE TABLE IF NOT EXISTS rule_definition (
  rule_definition_id VARCHAR(120) PRIMARY KEY,
  rule_set_version_id VARCHAR(100) NOT NULL,
  rule_code VARCHAR(120) NOT NULL,
  rule_type VARCHAR(80) NOT NULL,
  target_allergen_id VARCHAR(80),
  pattern_text VARCHAR(500) NOT NULL,
  severity VARCHAR(40) NOT NULL,
  is_active CHAR(1) NOT NULL,
  description VARCHAR(800),
  UNIQUE (rule_set_version_id, rule_code),
  FOREIGN KEY (rule_set_version_id) REFERENCES rule_set_version(rule_set_version_id),
  FOREIGN KEY (target_allergen_id) REFERENCES allergen(allergen_id)
);

CREATE TABLE IF NOT EXISTS ingredient_allergen (
  ingredient_allergen_id VARCHAR(120) PRIMARY KEY,
  ingredient_id VARCHAR(80) NOT NULL,
  allergen_id VARCHAR(80) NOT NULL,
  rule_set_version_id VARCHAR(100) NOT NULL,
  evidence_rule VARCHAR(500) NOT NULL,
  data_provenance_id VARCHAR(100) NOT NULL,
  UNIQUE (ingredient_id, allergen_id, rule_set_version_id),
  FOREIGN KEY (ingredient_id) REFERENCES ingredient(ingredient_id),
  FOREIGN KEY (allergen_id) REFERENCES allergen(allergen_id),
  FOREIGN KEY (rule_set_version_id) REFERENCES rule_set_version(rule_set_version_id),
  FOREIGN KEY (data_provenance_id) REFERENCES data_provenance(provenance_id)
);

CREATE TABLE IF NOT EXISTS ingredient_specification_version (
  specification_version_id VARCHAR(100) PRIMARY KEY,
  supplier_material_id VARCHAR(80) NOT NULL,
  version_number INT NOT NULL,
  lifecycle_status VARCHAR(40) NOT NULL,
  effective_date DATE NOT NULL,
  released_at DATETIME,
  created_by_user_id VARCHAR(80) NOT NULL,
  data_provenance_id VARCHAR(100) NOT NULL,
  UNIQUE (supplier_material_id, version_number),
  FOREIGN KEY (supplier_material_id) REFERENCES supplier_material(supplier_material_id),
  FOREIGN KEY (created_by_user_id) REFERENCES user_account(user_id),
  FOREIGN KEY (data_provenance_id) REFERENCES data_provenance(provenance_id)
);

CREATE TABLE IF NOT EXISTS spec_component (
  spec_component_id VARCHAR(120) PRIMARY KEY,
  specification_version_id VARCHAR(100) NOT NULL,
  ingredient_id VARCHAR(80) NOT NULL,
  raw_phrase VARCHAR(300) NOT NULL,
  match_rule VARCHAR(500) NOT NULL,
  match_status VARCHAR(40) NOT NULL,
  sequence_no INT NOT NULL,
  FOREIGN KEY (specification_version_id) REFERENCES ingredient_specification_version(specification_version_id),
  FOREIGN KEY (ingredient_id) REFERENCES ingredient(ingredient_id)
);

CREATE TABLE IF NOT EXISTS product (
  product_id VARCHAR(100) PRIMARY KEY,
  fdc_id BIGINT NOT NULL UNIQUE,
  gtin_upc VARCHAR(40),
  brand_owner VARCHAR(240) NOT NULL,
  brand_name VARCHAR(240),
  product_description VARCHAR(600) NOT NULL,
  branded_food_category VARCHAR(240),
  normalized_category VARCHAR(120) NOT NULL,
  market_country VARCHAR(100),
  publication_date DATE,
  source_ingredients_text TEXT NOT NULL,
  source_type VARCHAR(30) NOT NULL,
  fixture_group VARCHAR(80) NOT NULL,
  data_provenance_id VARCHAR(100) NOT NULL,
  current_formula_version_id VARCHAR(120),
  current_published_label_version_id VARCHAR(120),
  FOREIGN KEY (data_provenance_id) REFERENCES data_provenance(provenance_id)
);

CREATE TABLE IF NOT EXISTS formula_version (
  formula_version_id VARCHAR(120) PRIMARY KEY,
  product_id VARCHAR(100) NOT NULL,
  version_number INT NOT NULL,
  lifecycle_status VARCHAR(40) NOT NULL,
  is_current_released CHAR(1) NOT NULL DEFAULT 'N',
  created_by_user_id VARCHAR(80) NOT NULL,
  released_by_user_id VARCHAR(80),
  released_at DATETIME,
  data_provenance_id VARCHAR(100) NOT NULL,
  UNIQUE (product_id, version_number),
  FOREIGN KEY (product_id) REFERENCES product(product_id),
  FOREIGN KEY (created_by_user_id) REFERENCES user_account(user_id),
  FOREIGN KEY (released_by_user_id) REFERENCES user_account(user_id),
  FOREIGN KEY (data_provenance_id) REFERENCES data_provenance(provenance_id)
);

CREATE TABLE IF NOT EXISTS formula_item (
  formula_item_id VARCHAR(140) PRIMARY KEY,
  formula_version_id VARCHAR(120) NOT NULL,
  supplier_material_id VARCHAR(80) NOT NULL,
  specification_version_id VARCHAR(100) NOT NULL,
  sequence_no INT NOT NULL,
  quantity_value DECIMAL(12,4),
  quantity_unit VARCHAR(40),
  FOREIGN KEY (formula_version_id) REFERENCES formula_version(formula_version_id),
  FOREIGN KEY (supplier_material_id) REFERENCES supplier_material(supplier_material_id),
  FOREIGN KEY (specification_version_id) REFERENCES ingredient_specification_version(specification_version_id)
);

CREATE TABLE IF NOT EXISTS label_version (
  label_version_id VARCHAR(120) PRIMARY KEY,
  product_id VARCHAR(100) NOT NULL,
  formula_version_id VARCHAR(120) NOT NULL,
  rule_set_version_id VARCHAR(100) NOT NULL,
  jurisdiction_code VARCHAR(40) NOT NULL,
  version_number INT NOT NULL,
  raw_ingredient_text TEXT NOT NULL,
  lifecycle_status VARCHAR(40) NOT NULL,
  is_current_published CHAR(1) NOT NULL DEFAULT 'N',
  created_by_user_id VARCHAR(80) NOT NULL,
  created_at DATETIME NOT NULL,
  data_provenance_id VARCHAR(100) NOT NULL,
  UNIQUE (product_id, jurisdiction_code, version_number),
  FOREIGN KEY (product_id) REFERENCES product(product_id),
  FOREIGN KEY (formula_version_id) REFERENCES formula_version(formula_version_id),
  FOREIGN KEY (rule_set_version_id) REFERENCES rule_set_version(rule_set_version_id),
  FOREIGN KEY (created_by_user_id) REFERENCES user_account(user_id),
  FOREIGN KEY (data_provenance_id) REFERENCES data_provenance(provenance_id)
);

CREATE TABLE IF NOT EXISTS label_allergen_declaration (
  label_allergen_declaration_id VARCHAR(140) PRIMARY KEY,
  label_version_id VARCHAR(120) NOT NULL,
  allergen_id VARCHAR(80) NOT NULL,
  declaration_type VARCHAR(40) NOT NULL,
  declaration_source VARCHAR(80) NOT NULL,
  display_text VARCHAR(300),
  data_provenance_id VARCHAR(100) NOT NULL,
  UNIQUE (label_version_id, allergen_id, declaration_type),
  FOREIGN KEY (label_version_id) REFERENCES label_version(label_version_id),
  FOREIGN KEY (allergen_id) REFERENCES allergen(allergen_id),
  FOREIGN KEY (data_provenance_id) REFERENCES data_provenance(provenance_id)
);

CREATE TABLE IF NOT EXISTS validation_run (
  validation_run_id VARCHAR(140) PRIMARY KEY,
  label_version_id VARCHAR(120) NOT NULL,
  rule_set_version_id VARCHAR(100) NOT NULL,
  status VARCHAR(40) NOT NULL,
  ran_by_user_id VARCHAR(80) NOT NULL,
  ran_at DATETIME NOT NULL,
  summary VARCHAR(1000),
  data_provenance_id VARCHAR(100) NOT NULL,
  FOREIGN KEY (label_version_id) REFERENCES label_version(label_version_id),
  FOREIGN KEY (rule_set_version_id) REFERENCES rule_set_version(rule_set_version_id),
  FOREIGN KEY (ran_by_user_id) REFERENCES user_account(user_id),
  FOREIGN KEY (data_provenance_id) REFERENCES data_provenance(provenance_id)
);

CREATE TABLE IF NOT EXISTS validation_result (
  validation_result_id VARCHAR(160) PRIMARY KEY,
  validation_run_id VARCHAR(140) NOT NULL,
  rule_definition_id VARCHAR(120),
  result_code VARCHAR(140) NOT NULL,
  severity VARCHAR(40) NOT NULL,
  passed CHAR(1) NOT NULL,
  blocking CHAR(1) NOT NULL,
  message VARCHAR(1000) NOT NULL,
  FOREIGN KEY (validation_run_id) REFERENCES validation_run(validation_run_id),
  FOREIGN KEY (rule_definition_id) REFERENCES rule_definition(rule_definition_id)
);

CREATE TABLE IF NOT EXISTS change_request (
  change_request_id VARCHAR(120) PRIMARY KEY,
  change_request_code VARCHAR(120) NOT NULL UNIQUE,
  change_type VARCHAR(40) NOT NULL,
  status VARCHAR(40) NOT NULL,
  requested_at DATETIME NOT NULL,
  requested_by_user_id VARCHAR(80) NOT NULL,
  description VARCHAR(1000) NOT NULL,
  from_specification_version_id VARCHAR(100),
  to_specification_version_id VARCHAR(100),
  from_formula_version_id VARCHAR(120),
  to_formula_version_id VARCHAR(120),
  from_rule_set_version_id VARCHAR(100),
  to_rule_set_version_id VARCHAR(100),
  data_provenance_id VARCHAR(100) NOT NULL,
  FOREIGN KEY (requested_by_user_id) REFERENCES user_account(user_id),
  FOREIGN KEY (from_specification_version_id) REFERENCES ingredient_specification_version(specification_version_id),
  FOREIGN KEY (to_specification_version_id) REFERENCES ingredient_specification_version(specification_version_id),
  FOREIGN KEY (from_formula_version_id) REFERENCES formula_version(formula_version_id),
  FOREIGN KEY (to_formula_version_id) REFERENCES formula_version(formula_version_id),
  FOREIGN KEY (from_rule_set_version_id) REFERENCES rule_set_version(rule_set_version_id),
  FOREIGN KEY (to_rule_set_version_id) REFERENCES rule_set_version(rule_set_version_id),
  FOREIGN KEY (data_provenance_id) REFERENCES data_provenance(provenance_id)
);

CREATE TABLE IF NOT EXISTS impact_analysis_run (
  impact_analysis_run_id VARCHAR(120) PRIMARY KEY,
  run_code VARCHAR(120) NOT NULL UNIQUE,
  change_request_id VARCHAR(120) NOT NULL,
  rule_set_version_id VARCHAR(100) NOT NULL,
  status VARCHAR(40) NOT NULL,
  started_at DATETIME NOT NULL,
  completed_at DATETIME,
  executed_by_user_id VARCHAR(80) NOT NULL,
  data_provenance_id VARCHAR(100) NOT NULL,
  FOREIGN KEY (change_request_id) REFERENCES change_request(change_request_id),
  FOREIGN KEY (rule_set_version_id) REFERENCES rule_set_version(rule_set_version_id),
  FOREIGN KEY (executed_by_user_id) REFERENCES user_account(user_id),
  FOREIGN KEY (data_provenance_id) REFERENCES data_provenance(provenance_id)
);

CREATE TABLE IF NOT EXISTS impact_finding (
  impact_finding_id VARCHAR(140) PRIMARY KEY,
  impact_analysis_run_id VARCHAR(120) NOT NULL,
  product_id VARCHAR(100) NOT NULL,
  current_formula_version_id VARCHAR(120) NOT NULL,
  proposed_formula_version_id VARCHAR(120),
  current_label_version_id VARCHAR(120) NOT NULL,
  classification VARCHAR(40) NOT NULL,
  missing_allergen_codes JSON NOT NULL,
  explanation VARCHAR(1000) NOT NULL,
  data_provenance_id VARCHAR(100) NOT NULL,
  UNIQUE (impact_analysis_run_id, product_id),
  FOREIGN KEY (impact_analysis_run_id) REFERENCES impact_analysis_run(impact_analysis_run_id),
  FOREIGN KEY (product_id) REFERENCES product(product_id),
  FOREIGN KEY (current_formula_version_id) REFERENCES formula_version(formula_version_id),
  FOREIGN KEY (proposed_formula_version_id) REFERENCES formula_version(formula_version_id),
  FOREIGN KEY (current_label_version_id) REFERENCES label_version(label_version_id),
  FOREIGN KEY (data_provenance_id) REFERENCES data_provenance(provenance_id)
);

CREATE TABLE IF NOT EXISTS review_task (
  review_task_id VARCHAR(140) PRIMARY KEY,
  impact_finding_id VARCHAR(140) NOT NULL UNIQUE,
  product_id VARCHAR(100) NOT NULL,
  current_label_version_id VARCHAR(120) NOT NULL,
  draft_label_version_id VARCHAR(120),
  status VARCHAR(40) NOT NULL,
  assigned_to_user_id VARCHAR(80) NOT NULL,
  created_by_user_id VARCHAR(80) NOT NULL,
  created_at DATETIME NOT NULL,
  data_provenance_id VARCHAR(100) NOT NULL,
  FOREIGN KEY (impact_finding_id) REFERENCES impact_finding(impact_finding_id),
  FOREIGN KEY (product_id) REFERENCES product(product_id),
  FOREIGN KEY (current_label_version_id) REFERENCES label_version(label_version_id),
  FOREIGN KEY (draft_label_version_id) REFERENCES label_version(label_version_id),
  FOREIGN KEY (assigned_to_user_id) REFERENCES user_account(user_id),
  FOREIGN KEY (created_by_user_id) REFERENCES user_account(user_id),
  FOREIGN KEY (data_provenance_id) REFERENCES data_provenance(provenance_id)
);

CREATE TABLE IF NOT EXISTS approval_record (
  approval_record_id VARCHAR(140) PRIMARY KEY,
  label_version_id VARCHAR(120) NOT NULL,
  review_task_id VARCHAR(140) NOT NULL,
  decision VARCHAR(40) NOT NULL,
  decided_by_user_id VARCHAR(80) NOT NULL,
  decided_at DATETIME NOT NULL,
  comments VARCHAR(1000),
  data_provenance_id VARCHAR(100) NOT NULL,
  FOREIGN KEY (label_version_id) REFERENCES label_version(label_version_id),
  FOREIGN KEY (review_task_id) REFERENCES review_task(review_task_id),
  FOREIGN KEY (decided_by_user_id) REFERENCES user_account(user_id),
  FOREIGN KEY (data_provenance_id) REFERENCES data_provenance(provenance_id)
);

CREATE TABLE IF NOT EXISTS publication_record (
  publication_record_id VARCHAR(140) PRIMARY KEY,
  label_version_id VARCHAR(120) NOT NULL UNIQUE,
  published_by_user_id VARCHAR(80) NOT NULL,
  published_at DATETIME NOT NULL,
  publication_channel VARCHAR(80) NOT NULL,
  data_provenance_id VARCHAR(100) NOT NULL,
  FOREIGN KEY (label_version_id) REFERENCES label_version(label_version_id),
  FOREIGN KEY (published_by_user_id) REFERENCES user_account(user_id),
  FOREIGN KEY (data_provenance_id) REFERENCES data_provenance(provenance_id)
);

CREATE TABLE IF NOT EXISTS audit_event (
  audit_event_id VARCHAR(160) PRIMARY KEY,
  event_type VARCHAR(100) NOT NULL,
  entity_type VARCHAR(100) NOT NULL,
  entity_id VARCHAR(140) NOT NULL,
  event_at DATETIME NOT NULL,
  actor_user_id VARCHAR(80) NOT NULL,
  before_value JSON,
  after_value JSON,
  event_payload JSON NOT NULL,
  correlation_id VARCHAR(120),
  data_provenance_id VARCHAR(100) NOT NULL,
  FOREIGN KEY (actor_user_id) REFERENCES user_account(user_id),
  FOREIGN KEY (data_provenance_id) REFERENCES data_provenance(provenance_id)
);
