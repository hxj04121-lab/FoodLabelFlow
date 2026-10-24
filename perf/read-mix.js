// Read-heavy mix a Change Manager produces while reviewing a specification change:
// catalog list, product detail, the impact analysis report and a product's label history.
//
//   IMPACT_ANALYSIS_ID  required; perf/run.sh prepares it through the public API
//   PROFILE             smoke | baseline | capacity | ramp (default baseline)
//   VUS, DURATION       override the profile's virtual users / hold time
//   THRESHOLDS=off      measure without pass/fail thresholds
import { sleep, fail } from 'k6';
import { BASE_URL, CHANGE_MANAGER, enforce, getJson } from './lib.js';

const IMPACT_ANALYSIS_ID = __ENV.IMPACT_ANALYSIS_ID;
const PROFILE = __ENV.PROFILE || 'baseline';

const profiles = {
  smoke: { executor: 'constant-vus', vus: 2, duration: '30s' },
  baseline: { executor: 'constant-vus', vus: 10, duration: '2m' },
  // No think time: how many requests per second one stack serves before latency climbs.
  capacity: { executor: 'constant-vus', vus: 20, duration: '1m' },
  // Used by scripts/s4-autoscale-demo.sh: climb, hold long enough for the HPA to react, release.
  ramp: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '1m', target: Number(__ENV.VUS || 40) },
      { duration: __ENV.DURATION || '4m', target: Number(__ENV.VUS || 40) },
      { duration: '30s', target: 0 },
    ],
    gracefulRampDown: '10s',
  },
};

const scenario = { ...profiles[PROFILE] };
if (!profiles[PROFILE]) throw new Error(`unknown PROFILE ${PROFILE}`);
if (PROFILE !== 'ramp' && __ENV.VUS) scenario.vus = Number(__ENV.VUS);
if (PROFILE !== 'ramp' && __ENV.DURATION) scenario.duration = __ENV.DURATION;

// Per-endpoint p95 limits: about 3x the p95 measured with the capacity profile (20 VUs, no think
// time, ~990 req/s on one backend), rounded up, as headroom for cold JVMs and shared CI runners.
// Measurements: docs/evidence/S4-performance-and-autoscaling.md.
const thresholds = {
  http_req_failed: ['rate<0.01'],
  checks: ['rate>0.99'],
  'http_req_duration{name:catalog_list}': ['p(95)<100'],
  'http_req_duration{name:product_detail}': ['p(95)<75'],
  'http_req_duration{name:impact_report}': ['p(95)<200'],
  'http_req_duration{name:label_history}': ['p(95)<150'],
};

// With THRESHOLDS=off the per-endpoint entries still exist (always true) so the
// summary export keeps p50/p95/p99 for each endpoint.
const measureOnly = Object.fromEntries(Object.keys(thresholds).map((k) => [k, k.startsWith('http_req_duration') ? ['max>=0'] : []]));

export const options = {
  scenarios: { read_mix: scenario },
  thresholds: enforce() ? thresholds : measureOnly,
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  if (!IMPACT_ANALYSIS_ID) fail('IMPACT_ANALYSIS_ID is required; run perf/run.sh to prepare it');
  const products = getJson(CHANGE_MANAGER, '/api/catalog/products?limit=50', 'setup').json();
  if (!Array.isArray(products) || products.length === 0) fail(`no catalog products at ${BASE_URL}`);
  const report = getJson(CHANGE_MANAGER, `/api/v1/impact-analyses/${IMPACT_ANALYSIS_ID}`, 'setup').json();
  return {
    productIds: products.map((p) => p.product_id),
    impactedIds: report.findings.map((f) => f.productId),
  };
}

const pick = (list) => list[Math.floor(Math.random() * list.length)];

export default function (data) {
  const roll = Math.random();
  if (roll < 0.35) {
    getJson(CHANGE_MANAGER, '/api/catalog/products?limit=50', 'catalog_list');
  } else if (roll < 0.65) {
    getJson(CHANGE_MANAGER, `/api/catalog/products/${pick(data.productIds)}`, 'product_detail');
  } else if (roll < 0.85) {
    getJson(CHANGE_MANAGER, `/api/v1/impact-analyses/${IMPACT_ANALYSIS_ID}`, 'impact_report');
  } else {
    getJson(CHANGE_MANAGER, `/api/audit/products/${pick(data.impactedIds)}/label-history`, 'label_history');
  }
  if (PROFILE !== 'capacity') sleep(0.1 + Math.random() * 0.2);
}
