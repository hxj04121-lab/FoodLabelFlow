// Shared k6 settings for the SpecTrace performance scripts.
// Uses the local development header identities, so only point BASE_URL at a
// loopback stack, the CI Compose stack or a kubectl port-forward (see perf/README.md).
import http from 'k6/http';
import { check, fail } from 'k6';

export const BASE_URL = (__ENV.BASE_URL || 'http://127.0.0.1:8080').replace(/\/$/, '');
export const CHANGE_MANAGER = 'dev-external-change-manager';
export const DATA_ADMIN = 'dev-external-admin';
export const RULE_SET = 'ruleset_us_falcpa_demo_v1';

export function headers(subject, name) {
  return {
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-Auth-Provider': 'DEV_EXTERNAL',
      'X-External-Subject': subject,
    },
    tags: { name },
    timeout: '60s',
  };
}

export function getJson(subject, path, name) {
  const res = http.get(`${BASE_URL}${path}`, headers(subject, name));
  check(res, { [`${name} 200`]: (r) => r.status === 200 });
  return res;
}

export function postJson(subject, path, body, name) {
  const res = http.post(`${BASE_URL}${path}`, body === undefined ? null : JSON.stringify(body), headers(subject, name));
  if (!check(res, { [`${name} 2xx`]: (r) => r.status >= 200 && r.status < 300 })) {
    fail(`${name} returned ${res.status}: ${String(res.body).slice(0, 300)}`);
  }
  return res.json();
}

// Thresholds are opt-in per run so a first baseline can be measured without them.
export function enforce() {
  return (__ENV.THRESHOLDS || 'on') !== 'off';
}
