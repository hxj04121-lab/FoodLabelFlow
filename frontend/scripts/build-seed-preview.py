"""Export read-only preview records from the canonical seed, never runtime outcomes."""
import csv
import hashlib
import json
import re
from pathlib import Path

root = Path(__file__).resolve().parents[2]
source = root / 'backend/src/main/resources/db/migration/V3__baseline_seed.sql'
tables = {}
table = None
for line in source.read_text(encoding='utf-8').splitlines():
    match = re.match(r'INSERT INTO (\w+) \(([^)]+)\) VALUES', line)
    if match:
        table, columns = match[1], match[2].split(',')
        tables.setdefault(table, [])
    elif table and line.startswith('('):
        values = next(csv.reader([line[1:-2]], quotechar="'", doublequote=True))
        assert len(values) == len(columns), (table, len(values))
        tables[table].append(dict(zip(columns, [None if v == 'NULL' else v for v in values])))
        if line.endswith(';'):
            table = None
selected = ['supplier', 'supplier_material', 'ingredient_specification_version', 'spec_component', 'ingredient', 'product', 'formula_version', 'formula_item']
payload = {t: tables[t] for t in selected}
payload['source'] = {'file': 'V3__baseline_seed.sql', 'sha256': hashlib.sha256(source.read_bytes()).hexdigest(), 'mode': 'read-only seed preview'}
assert len(payload['product']) == 60
assert len(payload['formula_item']) == 160
out = root / 'frontend/src/data/seed-preview.json'
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding='utf-8')
print(f'Exported {len(payload["product"])} products and {len(payload["formula_item"])} formula items; no live API claim.')
