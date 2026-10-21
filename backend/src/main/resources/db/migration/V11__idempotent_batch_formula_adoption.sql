-- A successful product adoption is replayable by the natural business key. The row
-- is written in the same transaction as the immutable FormulaVersion and audit rows.
CREATE TABLE formula_adoption_batch_item (
  product_id VARCHAR(100) NOT NULL,
  source_formula_version_id VARCHAR(120) NOT NULL,
  target_specification_version_id VARCHAR(100) NOT NULL,
  formula_version_id VARCHAR(120) NOT NULL,
  adopted_at DATETIME NOT NULL,
  PRIMARY KEY (product_id, target_specification_version_id),
  CONSTRAINT fk_adoption_batch_product FOREIGN KEY (product_id) REFERENCES product(product_id),
  CONSTRAINT fk_adoption_batch_source FOREIGN KEY (source_formula_version_id) REFERENCES formula_version(formula_version_id),
  CONSTRAINT fk_adoption_batch_target FOREIGN KEY (target_specification_version_id)
    REFERENCES ingredient_specification_version(specification_version_id),
  CONSTRAINT fk_adoption_batch_result FOREIGN KEY (formula_version_id) REFERENCES formula_version(formula_version_id)
);
