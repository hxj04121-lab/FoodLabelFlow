-- SCRUM-13 / SCRUM-23 deterministic positive fixtures.
-- This script is self-contained after Flyway V1-V2 and deliberately does not load V3 seed data.

INSERT INTO data_provenance (
  provenance_id, source_type, source_dataset, source_record_id, source_url,
  source_snapshot_date, derivation_rule, synthetic_reason, created_at, created_by
) VALUES (
  'prov_s2_m2_positive_v1', 'PROJECT_SEEDED', 'S2-M2 positive golden fixtures',
  'SCRUM-23', NULL, '2026-09-15',
  'Exact test-only SOY/MILK/WHEAT mappings; no inferred or fallback rows.',
  'Deterministic positive contract evidence', '2026-09-15 00:00:00', 'SCRUM-23'
);

INSERT INTO user_account (
  user_id, username, display_name, email, auth_provider, external_auth_subject,
  password_hash, is_active, created_at
) VALUES (
  'user_s2_m2_fixture', 's2_m2_fixture', 'S2 M2 Fixture User', NULL,
  'DEV_EXTERNAL', 's2-m2-positive-fixture', NULL, 'Y', '2026-09-15 00:00:00'
);

INSERT INTO supplier (
  supplier_id, supplier_code, supplier_name, data_provenance_id
) VALUES (
  'supplier_s2_m2_positive', 'S2M2-POS', 'S2 M2 Positive Fixture Supplier',
  'prov_s2_m2_positive_v1'
);

INSERT INTO ingredient (
  ingredient_id, canonical_name, ingredient_kind, source_type, data_provenance_id
) VALUES
  ('ing_s2_m2_soy_lecithin', 'S2 M2 Golden Soy Lecithin', 'CANONICAL', 'PROJECT_SEEDED', 'prov_s2_m2_positive_v1'),
  ('ing_s2_m2_milk_powder', 'S2 M2 Golden Whole Milk Powder', 'CANONICAL', 'PROJECT_SEEDED', 'prov_s2_m2_positive_v1'),
  ('ing_s2_m2_wheat_flour', 'S2 M2 Golden Enriched Wheat Flour', 'CANONICAL', 'PROJECT_SEEDED', 'prov_s2_m2_positive_v1');

INSERT INTO supplier_material (
  supplier_material_id, supplier_id, ingredient_id, material_code,
  material_name, material_description, data_provenance_id
) VALUES
  ('mat_s2_m2_soy', 'supplier_s2_m2_positive', 'ing_s2_m2_soy_lecithin',
   'S2M2-SOY', 'Golden soy lecithin', 'Exact positive SOY source material', 'prov_s2_m2_positive_v1'),
  ('mat_s2_m2_milk', 'supplier_s2_m2_positive', 'ing_s2_m2_milk_powder',
   'S2M2-MILK', 'Golden whole milk powder', 'Exact positive MILK source material', 'prov_s2_m2_positive_v1'),
  ('mat_s2_m2_wheat', 'supplier_s2_m2_positive', 'ing_s2_m2_wheat_flour',
   'S2M2-WHEAT', 'Golden enriched wheat flour', 'Exact positive WHEAT source material', 'prov_s2_m2_positive_v1');

INSERT INTO allergen (
  allergen_id, allergen_code, display_name, jurisdiction_code, data_provenance_id
) VALUES
  ('all_soy', 'SOY', 'Soy', 'US', 'prov_s2_m2_positive_v1'),
  ('all_milk', 'MILK', 'Milk', 'US', 'prov_s2_m2_positive_v1'),
  ('all_wheat', 'WHEAT', 'Wheat', 'US', 'prov_s2_m2_positive_v1');

INSERT INTO rule_set_version (
  rule_set_version_id, rule_set_code, version_number, jurisdiction_code,
  lifecycle_status, effective_from, effective_to, is_demo_only,
  description, data_provenance_id
) VALUES (
  'ruleset_s2_m2_positive_v1', 'S2_M2_POSITIVE', '1', 'US', 'ACTIVE',
  '2026-01-01', NULL, 'Y', 'Deterministic positive declaration fixture rules',
  'prov_s2_m2_positive_v1'
);

INSERT INTO rule_definition (
  rule_definition_id, rule_set_version_id, rule_code, rule_type,
  target_allergen_id, pattern_text, severity, is_active, description
) VALUES
  ('rule_s2_m2_milk_decl', 'ruleset_s2_m2_positive_v1', 'MILK_DECLARATION_PRESENT',
   'LABEL_DECLARATION_VALIDATION', 'all_milk', 'MILK', 'INFO', 'Y', 'MILK must be declared'),
  ('rule_s2_m2_soy_decl', 'ruleset_s2_m2_positive_v1', 'SOY_DECLARATION_PRESENT',
   'LABEL_DECLARATION_VALIDATION', 'all_soy', 'SOY', 'INFO', 'Y', 'SOY must be declared'),
  ('rule_s2_m2_wheat_decl', 'ruleset_s2_m2_positive_v1', 'WHEAT_DECLARATION_PRESENT',
   'LABEL_DECLARATION_VALIDATION', 'all_wheat', 'WHEAT', 'INFO', 'Y', 'WHEAT must be declared');

INSERT INTO ingredient_allergen (
  ingredient_allergen_id, ingredient_id, allergen_id, rule_set_version_id,
  evidence_rule, data_provenance_id
) VALUES
  ('ia_s2_m2_soy_v1', 'ing_s2_m2_soy_lecithin', 'all_soy', 'ruleset_s2_m2_positive_v1',
   'fixture mapping: soy lecithin -> SOY', 'prov_s2_m2_positive_v1'),
  ('ia_s2_m2_milk_v1', 'ing_s2_m2_milk_powder', 'all_milk', 'ruleset_s2_m2_positive_v1',
   'fixture mapping: whole milk powder -> MILK', 'prov_s2_m2_positive_v1'),
  ('ia_s2_m2_wheat_v1', 'ing_s2_m2_wheat_flour', 'all_wheat', 'ruleset_s2_m2_positive_v1',
   'fixture mapping: enriched wheat flour -> WHEAT', 'prov_s2_m2_positive_v1');

INSERT INTO ingredient_specification_version (
  specification_version_id, supplier_material_id, version_number, lifecycle_status,
  effective_date, released_at, created_by_user_id, data_provenance_id
) VALUES
  ('spec_s2_m2_soy_v1', 'mat_s2_m2_soy', 1, 'RELEASED', '2026-09-15',
   '2026-09-15 00:00:00', 'user_s2_m2_fixture', 'prov_s2_m2_positive_v1'),
  ('spec_s2_m2_milk_v1', 'mat_s2_m2_milk', 1, 'RELEASED', '2026-09-15',
   '2026-09-15 00:00:00', 'user_s2_m2_fixture', 'prov_s2_m2_positive_v1'),
  ('spec_s2_m2_wheat_v1', 'mat_s2_m2_wheat', 1, 'RELEASED', '2026-09-15',
   '2026-09-15 00:00:00', 'user_s2_m2_fixture', 'prov_s2_m2_positive_v1');

INSERT INTO spec_component (
  spec_component_id, specification_version_id, ingredient_id, raw_phrase,
  match_rule, match_status, sequence_no
) VALUES
  ('component_s2_m2_soy_v1', 'spec_s2_m2_soy_v1', 'ing_s2_m2_soy_lecithin',
   'Soy lecithin', 'exact canonical fixture match', 'MATCHED', 1),
  ('component_s2_m2_milk_v1', 'spec_s2_m2_milk_v1', 'ing_s2_m2_milk_powder',
   'Whole milk powder', 'exact canonical fixture match', 'MATCHED', 1),
  ('component_s2_m2_wheat_v1', 'spec_s2_m2_wheat_v1', 'ing_s2_m2_wheat_flour',
   'Enriched wheat flour', 'exact canonical fixture match', 'MATCHED', 1);

INSERT INTO product (
  product_id, fdc_id, gtin_upc, brand_owner, brand_name, product_description,
  branded_food_category, normalized_category, market_country, publication_date,
  source_ingredients_text, source_type, fixture_group, data_provenance_id,
  current_formula_version_id, current_published_label_version_id
) VALUES
  -- S2M2-POS-SOY-001
  ('prod_s2_m2_soy', 990000301, NULL, 'S2 M2 Fixtures', 'Golden Soy',
   'Positive SOY golden fixture', 'Test fixture', 'TEST_FIXTURE', 'US', '2026-09-15',
   'Sunflower oil, soy lecithin.', 'PROJECT_SEEDED', 'NO_ACTION_BASELINE_SOY',
   'prov_s2_m2_positive_v1', NULL, NULL),
  -- S2M2-POS-MILK-001
  ('prod_s2_m2_milk', 990000302, NULL, 'S2 M2 Fixtures', 'Golden Milk',
   'Positive MILK golden fixture', 'Test fixture', 'TEST_FIXTURE', 'US', '2026-09-15',
   'Cocoa, whole milk powder.', 'PROJECT_SEEDED', 'NO_ACTION_BASELINE_SOY',
   'prov_s2_m2_positive_v1', NULL, NULL),
  -- S2M2-POS-WHEAT-001
  ('prod_s2_m2_wheat', 990000303, NULL, 'S2 M2 Fixtures', 'Golden Wheat',
   'Positive WHEAT golden fixture', 'Test fixture', 'TEST_FIXTURE', 'US', '2026-09-15',
   'Enriched wheat flour, sea salt.', 'PROJECT_SEEDED', 'NO_ACTION_BASELINE_SOY',
   'prov_s2_m2_positive_v1', NULL, NULL),
  -- S2M2-POS-MULTI-001
  ('prod_s2_m2_multi', 990000304, NULL, 'S2 M2 Fixtures', 'Golden Multi',
   'Positive multi-item golden fixture', 'Test fixture', 'TEST_FIXTURE', 'US', '2026-09-15',
   'Enriched wheat flour, whole milk powder, soy lecithin.',
   'PROJECT_SEEDED', 'NO_ACTION_BASELINE_SOY', 'prov_s2_m2_positive_v1', NULL, NULL);

INSERT INTO formula_version (
  formula_version_id, product_id, version_number, lifecycle_status,
  is_current_released, created_by_user_id, released_by_user_id,
  released_at, data_provenance_id
) VALUES
  ('formula_s2_m2_soy_v1', 'prod_s2_m2_soy', 1, 'RELEASED', 'Y',
   'user_s2_m2_fixture', 'user_s2_m2_fixture', '2026-09-15 00:00:00', 'prov_s2_m2_positive_v1'),
  ('formula_s2_m2_milk_v1', 'prod_s2_m2_milk', 1, 'RELEASED', 'Y',
   'user_s2_m2_fixture', 'user_s2_m2_fixture', '2026-09-15 00:00:00', 'prov_s2_m2_positive_v1'),
  ('formula_s2_m2_wheat_v1', 'prod_s2_m2_wheat', 1, 'RELEASED', 'Y',
   'user_s2_m2_fixture', 'user_s2_m2_fixture', '2026-09-15 00:00:00', 'prov_s2_m2_positive_v1'),
  ('formula_s2_m2_multi_v1', 'prod_s2_m2_multi', 1, 'RELEASED', 'Y',
   'user_s2_m2_fixture', 'user_s2_m2_fixture', '2026-09-15 00:00:00', 'prov_s2_m2_positive_v1');

INSERT INTO formula_item (
  formula_item_id, formula_version_id, supplier_material_id,
  specification_version_id, sequence_no, quantity_value, quantity_unit
) VALUES
  ('item_s2_m2_soy_v1', 'formula_s2_m2_soy_v1', 'mat_s2_m2_soy', 'spec_s2_m2_soy_v1', 1, 1.0000, 'kg'),
  ('item_s2_m2_milk_v1', 'formula_s2_m2_milk_v1', 'mat_s2_m2_milk', 'spec_s2_m2_milk_v1', 1, 1.0000, 'kg'),
  ('item_s2_m2_wheat_v1', 'formula_s2_m2_wheat_v1', 'mat_s2_m2_wheat', 'spec_s2_m2_wheat_v1', 1, 1.0000, 'kg'),
  ('item_s2_m2_multi_01', 'formula_s2_m2_multi_v1', 'mat_s2_m2_wheat', 'spec_s2_m2_wheat_v1', 1, 5.0000, 'kg'),
  ('item_s2_m2_multi_02', 'formula_s2_m2_multi_v1', 'mat_s2_m2_milk', 'spec_s2_m2_milk_v1', 2, 2.0000, 'kg'),
  ('item_s2_m2_multi_03', 'formula_s2_m2_multi_v1', 'mat_s2_m2_soy', 'spec_s2_m2_soy_v1', 3, 1.0000, 'kg');

INSERT INTO label_version (
  label_version_id, product_id, formula_version_id, rule_set_version_id,
  jurisdiction_code, version_number, raw_ingredient_text, lifecycle_status,
  is_current_published, created_by_user_id, created_at, data_provenance_id
) VALUES
  ('label_s2_m2_soy_v1', 'prod_s2_m2_soy', 'formula_s2_m2_soy_v1',
   'ruleset_s2_m2_positive_v1', 'US', 1, 'Sunflower oil, soy lecithin.',
   'DRAFT', 'N', 'user_s2_m2_fixture', '2026-09-15 00:00:00', 'prov_s2_m2_positive_v1'),
  ('label_s2_m2_milk_v1', 'prod_s2_m2_milk', 'formula_s2_m2_milk_v1',
   'ruleset_s2_m2_positive_v1', 'US', 1, 'Cocoa, whole milk powder.',
   'DRAFT', 'N', 'user_s2_m2_fixture', '2026-09-15 00:00:00', 'prov_s2_m2_positive_v1'),
  ('label_s2_m2_wheat_v1', 'prod_s2_m2_wheat', 'formula_s2_m2_wheat_v1',
   'ruleset_s2_m2_positive_v1', 'US', 1, 'Enriched wheat flour, sea salt.',
   'DRAFT', 'N', 'user_s2_m2_fixture', '2026-09-15 00:00:00', 'prov_s2_m2_positive_v1'),
  ('label_s2_m2_multi_v1', 'prod_s2_m2_multi', 'formula_s2_m2_multi_v1',
   'ruleset_s2_m2_positive_v1', 'US', 1,
   'Enriched wheat flour, whole milk powder, soy lecithin.',
   'DRAFT', 'N', 'user_s2_m2_fixture', '2026-09-15 00:00:00', 'prov_s2_m2_positive_v1');

INSERT INTO label_allergen_declaration (
  label_allergen_declaration_id, label_version_id, allergen_id,
  declaration_type, declaration_source, display_text, data_provenance_id
) VALUES
  ('decl_s2_m2_soy_v1', 'label_s2_m2_soy_v1', 'all_soy',
   'CONTAINS', 'FORMULA_DERIVED', 'Contains: Soy', 'prov_s2_m2_positive_v1'),
  ('decl_s2_m2_milk_v1', 'label_s2_m2_milk_v1', 'all_milk',
   'CONTAINS', 'FORMULA_DERIVED', 'Contains: Milk', 'prov_s2_m2_positive_v1'),
  ('decl_s2_m2_wheat_v1', 'label_s2_m2_wheat_v1', 'all_wheat',
   'CONTAINS', 'FORMULA_DERIVED', 'Contains: Wheat', 'prov_s2_m2_positive_v1'),
  ('decl_s2_m2_multi_milk_v1', 'label_s2_m2_multi_v1', 'all_milk',
   'CONTAINS', 'FORMULA_DERIVED', 'Contains: Milk, Soy, Wheat', 'prov_s2_m2_positive_v1'),
  ('decl_s2_m2_multi_soy_v1', 'label_s2_m2_multi_v1', 'all_soy',
   'CONTAINS', 'FORMULA_DERIVED', 'Contains: Milk, Soy, Wheat', 'prov_s2_m2_positive_v1'),
  ('decl_s2_m2_multi_wheat_v1', 'label_s2_m2_multi_v1', 'all_wheat',
   'CONTAINS', 'FORMULA_DERIVED', 'Contains: Milk, Soy, Wheat', 'prov_s2_m2_positive_v1');

UPDATE product
SET current_formula_version_id = CASE product_id
  WHEN 'prod_s2_m2_soy' THEN 'formula_s2_m2_soy_v1'
  WHEN 'prod_s2_m2_milk' THEN 'formula_s2_m2_milk_v1'
  WHEN 'prod_s2_m2_wheat' THEN 'formula_s2_m2_wheat_v1'
  WHEN 'prod_s2_m2_multi' THEN 'formula_s2_m2_multi_v1'
END
WHERE product_id IN (
  'prod_s2_m2_soy', 'prod_s2_m2_milk', 'prod_s2_m2_wheat', 'prod_s2_m2_multi'
);
