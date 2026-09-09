# Local formula editor

2026-09-08. Local-only implementation on XFY; no push or server write performed.

Proposal section 6.1 requires a product formula to reference supplier materials and exact specification versions, preserving released versions. The editor uses those relationships from the labelled V3 seed snapshot. Quantity and unit are optional based on nullable existing database columns; allowed units, totals, precision, duplicate-material policy and release eligibility remain unspecified. Non-negative numeric input is a local UX assumption, not a confirmed server rule. No version number, actor or publication result is fabricated.

Implemented: product selection, add/remove formula items (at least one), dependent specification options with reset on material changes, supplier context, optional quantity/unit, field errors with focus, content preview, return-to-edit, discard confirmation, and beforeunload protection. Opening preview does not save anything. Dialog modal interaction prevents background route navigation while editing.

Validation: TypeScript and Vite build passed. All six Playwright tests passed, including the new field validation, add/remove, dependent reset, preview, preserved input, discard and 390px overflow case. Mobile screenshot inspected. Existing five browser tests continue to pass. Native beforeunload handling is installed but native browser prompt behavior was not automated in this run. No live create/release acceptance is claimed.

Open /formulas and choose 创建配方预览. Server persistence and publication are the remaining integration work.

## Unsaved protection update

Independent Radix AlertDialog now handles cancel, close icon, Escape and outside-click dismissal without unmounting the editor. Continue is focused by default. Router navigation (including browser Back) uses React Router useBlocker; cancelling restores the existing route and values, confirming continues the blocked navigation. Browser reload uses native beforeunload, whose appearance is browser-controlled and is not guaranteed on every mobile lifecycle event. This is an in-memory form, not an autosave feature.

Added tests for clean dismissal, Escape, close icon, outside click, input preservation, reset after discard, browser Back cancellation/confirmation and native reload cancellation. Reload test uses a bounded navigation timeout because intentionally cancelled navigation does not finish. Vite build passes with a non-blocking bundle-size warning (approximately 537 kB uncompressed JS); code splitting remains a later optimization.
