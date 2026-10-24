// Write path of the S4 demo, timed end to end: the Data Admin batch-adopts a released
// Spec V2 for every current product, then the Change Manager records the ChangeRequest
// and runs impact analysis. Each iteration releases its own Spec V2 (V1 + Soy lecithin)
// first, so iterations never replay an earlier adoption. Writes data: only run it
// against a disposable stack (perf/run.sh fresh stack or the CI Compose stack).
//
//   ITERATIONS         default 3
//   EXPECT_PRODUCTS    expected adopted/relevant product count (default 40, the demo golden result)
//   THRESHOLDS=off     measure without pass/fail thresholds
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Trend } from 'k6/metrics';
import { CHANGE_MANAGER, DATA_ADMIN, RULE_SET, enforce, getJson, postJson } from './lib.js';

const adoptionDuration = new Trend('batch_adoption_duration', true);
const impactDuration = new Trend('impact_run_duration', true);
const EXPECT = Number(__ENV.EXPECT_PRODUCTS || 40);
const RUN_ID = __ENV.RUN_ID || `${Date.now()}`;

export const options = {
  scenarios: {
    write_path: { executor: 'per-vu-iterations', vus: 1, iterations: Number(__ENV.ITERATIONS || 3), maxDuration: '10m' },
  },
  thresholds: enforce()
    ? {
        http_req_failed: ['rate<0.01'],
        checks: ['rate==1.0'],
        // About 4x the warm p95 measured locally (240 ms / 110 ms): CI runs a single, cold iteration.
        batch_adoption_duration: ['p(95)<1000'],
        impact_run_duration: ['p(95)<500'],
      }
    : { batch_adoption_duration: ['max>=0'], impact_run_duration: ['max>=0'] },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const v1 = getJson(DATA_ADMIN, '/api/catalog/specifications/spec_chocolate_v1', 'setup').json();
  return {
    components: v1.components.map((c) => ({ ingredientId: c.ingredient_id, rawPhrase: c.raw_phrase, matchRule: c.match_rule })),
  };
}

function releaseSpecV2(components) {
  const marker = `PERF-${RUN_ID}-${exec.scenario.iterationInTest}`;
  const spec = postJson(DATA_ADMIN, '/api/catalog/specifications', {
    materialId: 'mat_chocolate_base',
    provenanceId: 'prov_project_seed',
    effectiveDate: new Date().toISOString().slice(0, 10),
    components: [...components, { ingredientId: 'ing_soy_lecithin', rawPhrase: 'Soy lecithin', matchRule: `${marker} Supplier Spec V2 declaration` }],
  }, 'spec_create');
  postJson(DATA_ADMIN, `/api/catalog/specifications/${spec.specification_version_id}/release`, undefined, 'spec_release');
  return { id: spec.specification_version_id, marker };
}

export default function (data) {
  const spec = releaseSpecV2(data.components);

  let started = Date.now();
  const adoption = postJson(DATA_ADMIN, `/api/catalog/specifications/${spec.id}/formula-adoptions`, undefined, 'batch_adoption');
  adoptionDuration.add(Date.now() - started);
  check(adoption, {
    [`adopted ${EXPECT}`]: (a) => a.adoptedCount === EXPECT && a.failedCount === 0,
  });

  const cr = postJson(CHANGE_MANAGER, '/api/v1/change-requests', {
    changeType: 'INGREDIENT_SPEC',
    supplierMaterialId: 'mat_chocolate_base',
    previousSpecificationVersionId: 'spec_chocolate_v1',
    targetSpecificationVersionId: spec.id,
    description: `${spec.marker}: Chocolate Base Spec V2 adds Soy Lecithin`,
  }, 'change_request');

  started = Date.now();
  const impact = postJson(CHANGE_MANAGER, `/api/v1/change-requests/${cr.changeRequestId}/impact-analyses`, { ruleSetVersionId: RULE_SET }, 'impact_run');
  impactDuration.add(Date.now() - started);
  check(impact, {
    [`impact ${EXPECT} relevant`]: (i) => i.status === 'COMPLETED' && i.relevantProductCount === EXPECT,
    'impact splits NO_ACTION / REVIEW_REQUIRED': (i) => i.noActionCount + i.reviewRequiredCount === i.relevantProductCount,
  });
  // Spaces the iterations out; also gives the web dashboard enough samples to export a report.
  sleep(3);
}
