# M1 catalog API v1 (review draft)

Base `/api/catalog`. Create JSON uses camelCase; response fields retain canonical
snake_case database names to support existing M3 preview mappings. IDs/version numbers
are assigned by the server. HTTP 201 means committed create; release returns 200.
Write operations require M4 CatalogIntegration; otherwise 503
`CATALOG_INTEGRATION_UNAVAILABLE`. These examples must not be described as live writes
until that bridge is installed. No request accepts a trusted actor ID.

| Method | Path | Result |
| --- | --- | --- |
| GET/POST | /suppliers | paginated list / create |
| GET | /suppliers/{id} | supplier |
| GET/POST | /materials | paginated list / create |
| GET | /materials/{id} | material |
| GET/POST | /specifications | paginated list / draft create |
| GET | /specifications/{id} | specification plus components |
| POST | /specifications/{id}/release | release draft specification |
| GET | /products | paginated list |
| GET | /products/{id} | product and current formula pointer |
| GET | /products/{id}/formulas | ordered version history |
| POST | /formulas | create immutable draft snapshot |
| GET | /formulas/{id} | formula plus items |
| POST | /formulas/{id}/release | atomic release |
| GET | /formulas/{id}/trace | formula and item/material/specification/supplier/evidence |

List query: `limit=50&offset=0`; limit 1..100, offset >=0. Default list is an array.
No update/delete API is supplied; corrections create a new version.

Supplier:
```json
{"code":"COURSE-SUP-01","name":"Course supplier","provenanceId":"prov_project_seed"}
```

Material (use the supplier ID returned above):
```json
{"supplierId":"<supplier_id>","ingredientId":"ing_cocoa","code":"COCOA-01","name":"Cocoa","description":"Course fixture","provenanceId":"prov_project_seed"}
```

Specification (components 1..100; explicit canonical ingredients and mapping evidence):
```json
{"materialId":"<supplier_material_id>","effectiveDate":"2026-09-01","provenanceId":"prov_project_seed","components":[{"ingredientId":"ing_cocoa","rawPhrase":"cocoa","matchRule":"Human verified course fixture"}]}
```

Formula (items 1..100; quantities and units either both omitted/null or both supplied;
positive DECIMAL(12,4), no silent rounding, no implicit conversions or 100% total rule):
```json
{"productId":"prod_usda_1106285","provenanceId":"prov_project_seed","items":[{"materialId":"mat_chocolate_base","specificationId":"spec_chocolate_v1","quantity":12.5,"unit":"kg"},{"materialId":"mat_soy_carrier","specificationId":"spec_soy_carrier_v1","quantity":null,"unit":null}]}
```

Formula release uses the pointer read from GET product, not an assumed V1:
```json
{"expectedCurrentFormulaId":"formula_1106285_v1"}
```
Use null only when the product currently has no released formula. A changed pointer
returns 409 CURRENT_FORMULA_CHANGED; refresh before retrying. A second release of an
already released version returns 409 VERSION_IMMUTABLE and performs no writes.

References must already exist; provenance IDs identify genuine existing source evidence.
`prov_project_seed` is appropriate only for course fixtures, not imported supplier claims.
Formula items must reference a released, effective specification belonging to the same
material. Trace never substitutes the newest specification for the referenced version.

Errors include `code` and `message`; validation does not expose database exception text.
Codes: INVALID_REQUEST, RESOURCE_NOT_FOUND, DATA_CONFLICT, VERSION_IMMUTABLE,
CURRENT_FORMULA_CHANGED, SPECIFICATION_MATERIAL_MISMATCH, SPECIFICATION_NOT_RELEASED,
SPECIFICATION_NOT_EFFECTIVE, AUTHORIZATION_DENIED, CATALOG_INTEGRATION_UNAVAILABLE.

Read-only smoke with a running backend:
```sh
curl --fail http://localhost:8080/api/catalog/formulas/formula_1106285_v1/trace
```
