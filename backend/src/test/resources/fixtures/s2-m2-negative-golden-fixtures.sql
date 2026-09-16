-- SCRUM-13 / SCRUM-24 deterministic negative fixtures.
-- Self-contained after Flyway V1-V2; exact inserts fail loudly on collisions.

INSERT INTO data_provenance (
  provenance_id, source_type, source_dataset, source_record_id, source_url,
  source_snapshot_date, derivation_rule, synthetic_reason, created_at, created_by
) VALUES (
  'prov_s2_m2_negative_v1', 'PROJECT_SEEDED', 'S2-M2 negative golden fixtures',
  'SCRUM-24', NULL, '2026-09-16',
  'Exact negative truth: missing declaration, inactive rule set, unmapped and ambiguous input.',
  'Deterministic blocking contract evidence', '2026-09-16 00:00:00', 'SCRUM-24'
);

INSERT INTO user_account (
  user_id, username, display_name, email, auth_provider, external_auth_subject,
  password_hash, is_active, created_at
) VALUES (
  'user_s2_m2_negative_fixture', 's2_m2_negative_fixture',
  'S2 M2 Negative Fixture User', NULL, 'DEV_EXTERNAL',
  's2-m2-negative-fixture', NULL, 'Y', '2026-09-16 00:00:00'
);

INSERT INTO supplier (
  supplier_id, supplier_code, supplier_name, data_provenance_id
) VALUES (
  'supplier_s2_m2_negative', 'S2M2-NEG', 'S2 M2 Negative Fixture Supplier',
  'prov_s2_m2_negative_v1'
);

INSERT INTO ingredient (
  ingredient_id, canonical_name, ingredient_kind, source_type, data_provenance_id
) VALUES
  ('ing_s2_m2_neg_soy_lecithin', 'S2 M2 Negative Soy Lecithin',
   'CANONICAL', 'PROJECT_SEEDED', 'prov_s2_m2_negative_v1'),
  ('ing_s2_m2_neg_unmapped', 'S2 M2 Unmapped Placeholder',
   'PLACEHOLDER', 'PROJECT_SEEDED', 'prov_s2_m2_negative_v1'),
  ('ing_s2_m2_neg_ambiguous', 'S2 M2 Ambiguous Placeholder',
   'PLACEHOLDER', 'PROJECT_SEEDED', 'prov_s2_m2_negative_v1');

INSERT INTO supplier_material (
  supplier_material_id, supplier_id, ingredient_id, material_code,
  material_name, material_description, data_provenance_id
) VALUES
  ('mat_s2_m2_neg_soy', 'supplier_s2_m2_negative', 'ing_s2_m2_neg_soy_lecithin',
   'S2M2-NEG-SOY', 'Negative fixture soy lecithin',
   'Known SOY source used to isolate declaration and rule-set failures',
   'prov_s2_m2_negative_v1'),
  ('mat_s2_m2_neg_unmapped', 'supplier_s2_m2_negative', 'ing_s2_m2_neg_unmapped',
   'S2M2-NEG-UNMAPPED', 'Negative fixture unmapped material',
   'Input with no canonical match', 'prov_s2_m2_negative_v1'),
  ('mat_s2_m2_neg_ambiguous', 'supplier_s2_m2_negative', 'ing_s2_m2_neg_ambiguous',
   'S2M2-NEG-AMBIGUOUS', 'Negative fixture ambiguous material',
   'Input with multiple canonical candidates', 'prov_s2_m2_negative_v1');

INSERT INTO allergen (
  allergen_id, allergen_code, display_name, jurisdiction_code, data_provenance_id
) VALUES (
  'all_s2_m2_neg_soy', 'SOY', 'Soy', 'US', 'prov_s2_m2_negative_v1'
);

INSERT INTO rule_set_version (
  rule_set_version_id, rule_set_code, version_number, jurisdiction_code,
  lifecycle_status, effective_from, effective_to, is_demo_only,
  description, data_provenance_id
) VALUES
  ('ruleset_s2_m2_negative_missing_decl_v1', 'S2_M2_NEGATIVE_MISSING_DECL', '1', 'US',
   'ACTIVE', '2026-01-01', NULL, 'Y',
   'Exact active rule for the missing SOY declaration fixture',
   'prov_s2_m2_negative_v1'),
  ('ruleset_s2_m2_negative_unmapped_v1', 'S2_M2_NEGATIVE_UNMAPPED', '1', 'US',
   'ACTIVE', '2026-01-01', NULL, 'Y',
   'Exact active rule for the unmapped ingredient fixture',
   'prov_s2_m2_negative_v1'),
  ('ruleset_s2_m2_negative_ambiguous_v1', 'S2_M2_NEGATIVE_AMBIGUOUS', '1', 'US',
   'ACTIVE', '2026-01-01', NULL, 'Y',
   'Exact active rule for the ambiguous ingredient fixture',
   'prov_s2_m2_negative_v1'),
  ('ruleset_s2_m2_negative_retired_v1', 'S2_M2_NEGATIVE_RETIRED', '1', 'US',
   'RETIRED', '2025-01-01', '2025-12-31', 'Y',
   'Exact inactive rule set; an active same-jurisdiction alternative must not replace it',
   'prov_s2_m2_negative_v1');

INSERT INTO rule_definition (
  rule_definition_id, rule_set_version_id, rule_code, rule_type,
  target_allergen_id, pattern_text, severity, is_active, description
) VALUES
  ('rule_s2_m2_neg_soy_decl', 'ruleset_s2_m2_negative_missing_decl_v1',
   'ALLERGEN_DECLARATION_MISSING', 'LABEL_DECLARATION_VALIDATION',
   'all_s2_m2_neg_soy', 'SOY', 'ERROR', 'Y',
   'A derived SOY fact requires a CONTAINS declaration'),
  ('rule_s2_m2_neg_unmapped', 'ruleset_s2_m2_negative_unmapped_v1',
   'INGREDIENT_UNMAPPED', 'INGREDIENT_TO_ALLERGEN',
   NULL, 'UNMAPPED', 'ERROR', 'Y',
   'An unmapped component is an input-level blocking failure'),
  ('rule_s2_m2_neg_ambiguous', 'ruleset_s2_m2_negative_ambiguous_v1',
   'INGREDIENT_AMBIGUOUS', 'INGREDIENT_TO_ALLERGEN',
   NULL, 'AMBIGUOUS', 'ERROR', 'Y',
   'An ambiguous component is an input-level blocking failure');

INSERT INTO ingredient_allergen (
  ingredient_allergen_id, ingredient_id, allergen_id, rule_set_version_id,
  evidence_rule, data_provenance_id
) VALUES
  ('ia_s2_m2_neg_soy_active_v1', 'ing_s2_m2_neg_soy_lecithin',
   'all_s2_m2_neg_soy', 'ruleset_s2_m2_negative_missing_decl_v1',
   'fixture mapping: soy lecithin -> SOY', 'prov_s2_m2_negative_v1'),
  ('ia_s2_m2_neg_soy_retired_v1', 'ing_s2_m2_neg_soy_lecithin',
   'all_s2_m2_neg_soy', 'ruleset_s2_m2_negative_retired_v1',
   'fixture mapping: soy lecithin -> SOY', 'prov_s2_m2_negative_v1');

INSERT INTO ingredient_specification_version (
  specification_version_id, supplier_material_id, version_number, lifecycle_status,
  effective_date, released_at, created_by_user_id, data_provenance_id
) VALUES
  ('spec_s2_m2_neg_soy_v1', 'mat_s2_m2_neg_soy', 1, 'RELEASED', '2026-09-16',
   '2026-09-16 00:00:00', 'user_s2_m2_negative_fixture', 'prov_s2_m2_negative_v1'),
  ('spec_s2_m2_neg_unmapped_v1', 'mat_s2_m2_neg_unmapped', 1, 'RELEASED', '2026-09-16',
   '2026-09-16 00:00:00', 'user_s2_m2_negative_fixture', 'prov_s2_m2_negative_v1'),
  ('spec_s2_m2_neg_ambiguous_v1', 'mat_s2_m2_neg_ambiguous', 1, 'RELEASED', '2026-09-16',
   '2026-09-16 00:00:00', 'user_s2_m2_negative_fixture', 'prov_s2_m2_negative_v1');

INSERT INTO spec_component (
  spec_component_id, specification_version_id, ingredient_id, raw_phrase,
  match_rule, match_status, sequence_no
) VALUES
  ('component_s2_m2_neg_soy_v1', 'spec_s2_m2_neg_soy_v1',
   'ing_s2_m2_neg_soy_lecithin', 'Soy lecithin',
   'exact canonical fixture match', 'MATCHED', 1),
  ('component_s2_m2_neg_unmapped_v1', 'spec_s2_m2_neg_unmapped_v1',
   'ing_s2_m2_neg_unmapped', 'Mystery protein blend',
   'no canonical match', 'UNMAPPED', 1),
  ('component_s2_m2_neg_ambiguous_v1', 'spec_s2_m2_neg_ambiguous_v1',
   'ing_s2_m2_neg_ambiguous', 'Natural flavor concentrate',
   'multiple canonical candidates', 'AMBIGUOUS', 1);

INSERT INTO product (
  product_id, fdc_id, gtin_upc, brand_owner, brand_name, product_description,
  branded_food_category, normalized_category, market_country, publication_date,
  source_ingredients_text, source_type, fixture_group, data_provenance_id,
  current_formula_version_id, current_published_label_version_id
) VALUES
  -- S2M2-NEG-MISSING-DECL-001
  ('prod_s2_m2_neg_missing_decl', 990000401, NULL, 'S2 M2 Fixtures',
   'Golden Missing Declaration', 'Derived SOY with no declaration',
   'Test fixture', 'TEST_FIXTURE', 'US', '2026-09-16',
   'Sunflower oil, soy lecithin.', 'PROJECT_SEEDED', 'REVIEW_REQUIRED_BASELINE_NO_SOY',
   'prov_s2_m2_negative_v1', NULL, NULL),
  -- S2M2-NEG-NO-ACTIVE-RULESET-001
  ('prod_s2_m2_neg_no_active', 990000402, NULL, 'S2 M2 Fixtures',
   'Golden No Active Rule Set', 'Valid SOY data bound to a retired rule set',
   'Test fixture', 'TEST_FIXTURE', 'US', '2026-09-16',
   'Sunflower oil, soy lecithin.', 'PROJECT_SEEDED', 'NO_ACTION_BASELINE_SOY',
   'prov_s2_m2_negative_v1', NULL, NULL),
  -- S2M2-NEG-UNMAPPED-001
  ('prod_s2_m2_neg_unmapped', 990000403, NULL, 'S2 M2 Fixtures',
   'Golden Unmapped Ingredient', 'Unmapped ingredient input',
   'Test fixture', 'TEST_FIXTURE', 'US', '2026-09-16',
   'Mystery protein blend.', 'PROJECT_SEEDED', 'NEGATIVE_CONTROL_NO_CHOCOLATE',
   'prov_s2_m2_negative_v1', NULL, NULL),
  -- S2M2-NEG-AMBIGUOUS-001
  ('prod_s2_m2_neg_ambiguous', 990000404, NULL, 'S2 M2 Fixtures',
   'Golden Ambiguous Ingredient', 'Ambiguous ingredient input',
   'Test fixture', 'TEST_FIXTURE', 'US', '2026-09-16',
   'Natural flavor concentrate.', 'PROJECT_SEEDED', 'NEGATIVE_CONTROL_NO_CHOCOLATE',
   'prov_s2_m2_negative_v1', NULL, NULL);

INSERT INTO formula_version (
  formula_version_id, product_id, version_number, lifecycle_status,
  is_current_released, created_by_user_id, released_by_user_id,
  released_at, data_provenance_id
) VALUES
  ('formula_s2_m2_neg_missing_decl_v1', 'prod_s2_m2_neg_missing_decl', 1,
   'RELEASED', 'Y', 'user_s2_m2_negative_fixture', 'user_s2_m2_negative_fixture',
   '2026-09-16 00:00:00', 'prov_s2_m2_negative_v1'),
  ('formula_s2_m2_neg_no_active_v1', 'prod_s2_m2_neg_no_active', 1,
   'RELEASED', 'Y', 'user_s2_m2_negative_fixture', 'user_s2_m2_negative_fixture',
   '2026-09-16 00:00:00', 'prov_s2_m2_negative_v1'),
  ('formula_s2_m2_neg_unmapped_v1', 'prod_s2_m2_neg_unmapped', 1,
   'RELEASED', 'Y', 'user_s2_m2_negative_fixture', 'user_s2_m2_negative_fixture',
   '2026-09-16 00:00:00', 'prov_s2_m2_negative_v1'),
  ('formula_s2_m2_neg_ambiguous_v1', 'prod_s2_m2_neg_ambiguous', 1,
   'RELEASED', 'Y', 'user_s2_m2_negative_fixture', 'user_s2_m2_negative_fixture',
   '2026-09-16 00:00:00', 'prov_s2_m2_negative_v1');

INSERT INTO formula_item (
  formula_item_id, formula_version_id, supplier_material_id,
  specification_version_id, sequence_no, quantity_value, quantity_unit
) VALUES
  ('item_s2_m2_neg_missing_decl_v1', 'formula_s2_m2_neg_missing_decl_v1',
   'mat_s2_m2_neg_soy', 'spec_s2_m2_neg_soy_v1', 1, 1.0000, 'kg'),
  ('item_s2_m2_neg_no_active_v1', 'formula_s2_m2_neg_no_active_v1',
   'mat_s2_m2_neg_soy', 'spec_s2_m2_neg_soy_v1', 1, 1.0000, 'kg'),
  ('item_s2_m2_neg_unmapped_v1', 'formula_s2_m2_neg_unmapped_v1',
   'mat_s2_m2_neg_unmapped', 'spec_s2_m2_neg_unmapped_v1', 1, 1.0000, 'kg'),
  ('item_s2_m2_neg_ambiguous_v1', 'formula_s2_m2_neg_ambiguous_v1',
   'mat_s2_m2_neg_ambiguous', 'spec_s2_m2_neg_ambiguous_v1', 1, 1.0000, 'kg');

INSERT INTO label_version (
  label_version_id, product_id, formula_version_id, rule_set_version_id,
  jurisdiction_code, version_number, raw_ingredient_text, lifecycle_status,
  is_current_published, created_by_user_id, created_at, data_provenance_id
) VALUES
  ('label_s2_m2_neg_missing_decl_v1', 'prod_s2_m2_neg_missing_decl',
   'formula_s2_m2_neg_missing_decl_v1', 'ruleset_s2_m2_negative_missing_decl_v1',
   'US', 1, 'Sunflower oil, soy lecithin.', 'DRAFT', 'N',
   'user_s2_m2_negative_fixture', '2026-09-16 00:00:00', 'prov_s2_m2_negative_v1'),
  ('label_s2_m2_neg_no_active_v1', 'prod_s2_m2_neg_no_active',
   'formula_s2_m2_neg_no_active_v1', 'ruleset_s2_m2_negative_retired_v1',
   'US', 1, 'Sunflower oil, soy lecithin.', 'DRAFT', 'N',
   'user_s2_m2_negative_fixture', '2026-09-16 00:00:00', 'prov_s2_m2_negative_v1'),
  ('label_s2_m2_neg_unmapped_v1', 'prod_s2_m2_neg_unmapped',
   'formula_s2_m2_neg_unmapped_v1', 'ruleset_s2_m2_negative_unmapped_v1',
   'US', 1, 'Mystery protein blend.', 'DRAFT', 'N',
   'user_s2_m2_negative_fixture', '2026-09-16 00:00:00', 'prov_s2_m2_negative_v1'),
  ('label_s2_m2_neg_ambiguous_v1', 'prod_s2_m2_neg_ambiguous',
   'formula_s2_m2_neg_ambiguous_v1', 'ruleset_s2_m2_negative_ambiguous_v1',
   'US', 1, 'Natural flavor concentrate.', 'DRAFT', 'N',
   'user_s2_m2_negative_fixture', '2026-09-16 00:00:00', 'prov_s2_m2_negative_v1');

-- Only the no-active-rule-set case has a correct declaration, isolating its 422 cause.
INSERT INTO label_allergen_declaration (
  label_allergen_declaration_id, label_version_id, allergen_id,
  declaration_type, declaration_source, display_text, data_provenance_id
) VALUES (
  'decl_s2_m2_neg_no_active_soy_v1', 'label_s2_m2_neg_no_active_v1',
  'all_s2_m2_neg_soy', 'CONTAINS', 'FORMULA_DERIVED', 'Contains: Soy',
  'prov_s2_m2_negative_v1'
);

UPDATE product
SET current_formula_version_id = CASE product_id
  WHEN 'prod_s2_m2_neg_missing_decl' THEN 'formula_s2_m2_neg_missing_decl_v1'
  WHEN 'prod_s2_m2_neg_no_active' THEN 'formula_s2_m2_neg_no_active_v1'
  WHEN 'prod_s2_m2_neg_unmapped' THEN 'formula_s2_m2_neg_unmapped_v1'
  WHEN 'prod_s2_m2_neg_ambiguous' THEN 'formula_s2_m2_neg_ambiguous_v1'
END
WHERE product_id IN (
  'prod_s2_m2_neg_missing_decl',
  'prod_s2_m2_neg_no_active',
  'prod_s2_m2_neg_unmapped',
  'prod_s2_m2_neg_ambiguous'
);
