# Private hosted course demonstrations

The frontend keeps course identity switching and command controls enabled on `localhost`, `127.0.0.1` and `[::1]`. Other origins are disabled by default. A trusted course deployment can opt into exact HTTPS origins at **build time**:

```sh
VITE_DEMO_ALLOWED_ORIGINS=https://demo.example.invalid docker compose build frontend
```

This example only builds the image. Use a reviewed server deployment configuration with loopback-only ingress, site-wide HTTPS access protection covering both pages and APIs, and isolated demonstration data before starting a hosted stack. The repository's development Compose configuration is not a cloud exposure policy.

`VITE_DEMO_ALLOWED_ORIGINS` is a comma-separated list of canonical origins, for example `https://demo.example.invalid,https://second.example.invalid:8443`. Whitespace around entries is ignored. Scheme, hostname and port must exactly equal the browser's `location.origin`. HTTP, wildcard suffixes, paths, query strings, fragments and credential-bearing URLs do not enable a hosted origin. Use the origin without a trailing slash; default HTTPS port 443 is omitted by the browser.

The setting is public frontend configuration, never a password or token. Vite compiles it into JavaScript. Changing a running container's environment does not change an already built bundle: rebuild and replace the frontend image. The frontend Dockerfile accepts the build argument with an empty default; Compose forwards the same setting.

All environment checks share `src/lib/local-demo.ts`. Its existing-course-compatible `isLocalDemoHost()` name includes an explicitly enabled hosted demo. It gates role switching, formula persistence, label creation and returned-label revisions, validation, impact commands, and review/publication controls. Consent, verified permissions, exact-version bindings, failed-validation guards, uncertain-command handling and backend maker-checker policy remain independent requirements. Formula commands retain the separate explicit Data Admin consent and fixed `dev-external-admin` mapping; the header's selected label workflow role is not the formula command identity.

This allowlist is **not authentication or authorization**. Development identity headers are appropriate only behind independent site-wide access protection for trusted course participants. They are not a production login or a binding between a real person and a business role. Backend `prod`/`production` identity rejection is unchanged. Do not remove it or grant extra role permissions to make a demonstration pass.

For the complete Change impact demonstration, the reviewed Change Manager selector and any required specification, batch-adoption or history pages must also enter main. They are separate from the origin setting. When integrating PR92, retain this origin-aware helper and its disabled-address messaging; its additional `isLocalDemoHost()` callers should use the same policy. Recheck every command path and rebuild after merging, rather than treating a conflict-free merge as verification.

## Verification scope

```sh
cd frontend
npm ci
npm run build
npx playwright test tests/demo-origin-policy.spec.ts
npx playwright test --config playwright.demo-origin.config.ts
```

The policy tests cover default-deny, loopback compatibility and exact HTTPS origin boundaries. The dedicated UI regression builds a production bundle with an explicit origin setting, runs a separate local preview server, and serves its assets through intercepted browser URLs. All APIs use explicit fixtures; no request is sent to the real hosted site. It checks domain role selection, consent-gated label/formula writes, validation/workflow controls, role denial, and the separate returned-revision path. It does not prove a cloud deployment, TLS, access authentication, database persistence or live backend permission enforcement.

After required changes merge, pin the successful main SHA and image IDs, verify the compiled frontend at the actual HTTPS origin behind access protection, and perform a real save/reload and independent review/publication rehearsal. Keep server, DNS, release, migration, browser and negative-test evidence together. Never run a destructive fresh/reset fixture against the persistent hosted database.
