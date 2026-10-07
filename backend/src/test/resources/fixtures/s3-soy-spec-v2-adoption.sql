-- S3 SOY scenario fixture until M2's real adoption (SCRUM-55/56) is on main.
-- Releases Chocolate Base Spec V2 = V1 components + Soy Lecithin, then adopts it into a
-- new current RELEASED FormulaVersion N+1 for every product whose current formula uses
-- Chocolate Base. N stays RELEASED (not current) and keeps backing the published label.
-- Only for test classes that own their MySQL container: these rows are committed.

INSERT INTO ingredient_specification_version (specification_version_id, supplier_material_id, version_number,
    lifecycle_status, effective_date, released_at, created_by_user_id, data_provenance_id)
VALUES ('spec_chocolate_v2', 'mat_chocolate_base', 2, 'RELEASED', '2026-09-01', '2026-09-01 09:00:00',
    'user_admin', 'prov_project_seed');

INSERT INTO spec_component (spec_component_id, specification_version_id, ingredient_id, raw_phrase, match_rule,
    match_status, sequence_no)
SELECT REPLACE(spec_component_id, '_v1_', '_v2_'), 'spec_chocolate_v2', ingredient_id, raw_phrase, match_rule,
    match_status, sequence_no
FROM spec_component WHERE specification_version_id = 'spec_chocolate_v1';

INSERT INTO spec_component (spec_component_id, specification_version_id, ingredient_id, raw_phrase, match_rule,
    match_status, sequence_no)
VALUES ('sc_chocolate_v2_soy_lecithin', 'spec_chocolate_v2', 'ing_soy_lecithin', 'Soy lecithin',
    'Project fixture component', 'MATCHED', 3);

INSERT INTO formula_version (formula_version_id, product_id, version_number, lifecycle_status, is_current_released,
    created_by_user_id, released_by_user_id, released_at, data_provenance_id)
SELECT CONCAT(fv.formula_version_id, 'n1soy'), fv.product_id, fv.version_number + 1, 'RELEASED', 'N',
    'user_admin', 'user_admin', '2026-09-02 09:00:00', 'prov_project_seed'
FROM product p
JOIN formula_version fv ON fv.formula_version_id = p.current_formula_version_id
WHERE fv.is_current_released = 'Y'
  AND EXISTS (SELECT 1 FROM formula_item fi
              WHERE fi.formula_version_id = fv.formula_version_id AND fi.supplier_material_id = 'mat_chocolate_base');

INSERT INTO formula_item (formula_item_id, formula_version_id, supplier_material_id, specification_version_id,
    sequence_no, quantity_value, quantity_unit)
SELECT CONCAT(fi.formula_item_id, 'n1soy'), n1.formula_version_id, fi.supplier_material_id,
    CASE WHEN fi.supplier_material_id = 'mat_chocolate_base' THEN 'spec_chocolate_v2' ELSE fi.specification_version_id END,
    fi.sequence_no, fi.quantity_value, fi.quantity_unit
FROM formula_item fi
JOIN formula_version n1 ON n1.formula_version_id = CONCAT(fi.formula_version_id, 'n1soy');

UPDATE formula_version n
JOIN formula_version n1 ON n1.formula_version_id = CONCAT(n.formula_version_id, 'n1soy')
SET n.is_current_released = 'N';

UPDATE formula_version SET is_current_released = 'Y' WHERE formula_version_id LIKE '%n1soy';

UPDATE product p
JOIN formula_version n1 ON n1.product_id = p.product_id AND n1.formula_version_id LIKE '%n1soy'
SET p.current_formula_version_id = n1.formula_version_id;
