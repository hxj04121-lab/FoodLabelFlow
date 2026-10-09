# Persisted current validation run

## Problem and scope

`ValidationApplicationService` creates a random UUID and stores `ranAt` at whole-second precision. The previous `JdbcLabelReviewCommandRepository.hasPassingValidation` treated a lexicographically larger UUID in the same second as a later validation. A PASS followed by a FAIL could therefore continue to satisfy review submission; the reverse order could deny a valid submission. Timestamp precision alone cannot establish causal order between concurrent writers.

This change depends on PR79's forward V9 migration and returned-draft isolation. It is a separate branch from M4's PR78. It shares PR78's intended approval/publication recheck behavior while fixing the underlying selection mechanism; it does not modify M4's branch.

## Decision

V10 creates `validation_current_run`, keyed by the exact `(label_version_id, rule_set_version_id)`, with an explicit `validation_run_id` association. A composite foreign key enforces that the associated run has that exact target. `validation_run_registration` records each supported new run once, with that same exact target. The supporting unique index changes no existing run fields. A run INSERT, its registration, current association, per-target counter, results and synchronous audit commit or roll back together. Selection no longer compares `ran_at` or UUID values. The counter describes newly observed supported insert transactions for that target; it does not reconstruct the full historical order.

The ordinary-account `sp_insert_validation_run` routine locks the associated product and label before inserting the new run and updating its registration, association and counter in the caller transaction. It does not start or commit a transaction. `JdbcValidationRunRepository.save` joins or starts a Spring transaction and requires this V10 routine; there is no fallback for a missing routine. Java validation takes the same product-first lock before label/formula locks. Concurrent supported writers for the same product cannot publish an uncommitted association to consumers. Replaying an existing run ID fails at the existing primary key and cannot advance the association; there is no procedure which registers an existing raw run later.

The association also stores the number of target runs observed by that new validation. Consumers take a current locking read of the exact target range and require that its count still matches. An unsupported raw INSERT therefore invalidates an older association, even when it bypasses supported writers. A new supported validation may establish a fresh association after observing previously committed raw runs; it does not register those raw rows or invent their order. The label lock held before the new INSERT blocks concurrent raw child INSERTs through their existing label foreign key until commit, and target-range reads use the dedicated composite index. These concurrency properties require the independent real MySQL wait regressions.

Review submission, APPROVE and publication read the association with `FOR SHARE`, join the run's exact label/rule-set binding and the label's actual bound rule set, and require PASSED. A completed later FAIL blocks all three actions. REQUEST_CHANGES and REJECT remain available without a passing current run. Publication performs this check before superseding any prior label, updating a pointer, or writing publication evidence.

The initial product identity lookup is an ordinary read. Subsequent label, formula, declarations, rule-set and allergen-mapping reads use locking reads, so a product-lock wait cannot leave validation using a prior REPEATABLE READ snapshot of committed mappings.

## Historical upgrade

No existing `validation_run`, `validation_result`, audit, approval or publication row is updated. All existing runs receive a derived LEGACY identity registration with NULL sequence, and target counts are observed without treating counts as historical order. For each existing target, a unique run at its maximum historical timestamp is associated as `LEGACY_UNIQUE` with counter zero. Multiple runs at that maximum timestamp are `LEGACY_AMBIGUOUS` with a NULL association, regardless of their statuses or IDs. Such a target requires a new validation. The migration does not guess an order for tied historical runs or assign them fabricated counters.

The composite run foreign key uses `ON DELETE CASCADE`; deleting the current run removes the derived association and leaves the target unable to satisfy a PASS gate. There is no automatic fallback to an older PASS. Label/rule-set foreign keys also cascade only the derived association when an isolated fixture is removed. These constraints support existing independent test cleanup; they do not authorize deleting business history.

## Direct SQL compatibility

Java persistence and `sp_record_label_validation_pass`/`sp_record_label_validation_fail` use the same shared new-run insertion routine. The two legacy helpers retain their outer START TRANSACTION, results, attributable audit and COMMIT/ROLLBACK behavior. `sp_assert_current_validation_pass` uses a locking read, exact bound-rule-set association, registered run identity/sequence and matching observed target count inside its caller's transaction.

V10 preserves V9's returned-label resubmission guard, actor permission, lifecycle update, task update and audit behavior in `sp_submit_label_for_review`, replacing its historical-any-PASS check with the shared current-run assertion. It also preserves V4's legacy `sp_record_label_decision` body, adding that assertion for APPROVE after maker-checker validation. Its legacy task closure semantics remain; it is not the modern Spring review-task publication workflow. V7 removed `sp_publish_label`; this change does not recreate it.

V10 redefines `sp_assert_current_label_version` with separate product-then-label locks and a locking latest-version read. It retains the missing-label, current-formula and latest-label guards, including rejection when no current formula exists. Java submission and decision use the same explicit single-product lock before loading the target; a JOIN's optimizer order is not used to establish lock order.

Arbitrary manual changes to lifecycle rows or derived associations are not new supported command paths. This change leaves database credentials, permissions, isolation settings and other security configuration unchanged.

## Contracts and verification

Validation POST/GET fields, UUID identity, second-precision `ranAt`, actor/provenance binding and immutable historical GET representations remain unchanged. The additional counter and association are internal persistence details. The workflow repository adds `hasPassingValidationForBoundRuleSet` without changing target records or HTTP resources.

Required real MySQL/HTTP regressions cover both same-second result orders; validation versus workflow lock waits; a committed mapping change observed after a wait; exact label/rule-set separation; run/registration/pointer/results/audit rollback; duplicate-ID replay; concurrent supported writers; raw INSERT invalidation/recovery and foreign-key blocking; independent products; APPROVE/publication rejection before mutation; REQUEST_CHANGES/REJECT availability; direct SQL guards; and V9-to-V10 unique/ambiguous history with unchanged old evidence. The four historical target-2 fixture tests which write runs explicitly install the exact V10 persistence prefix using the official Flyway MySQL parser, preserving their fixture and HTTP contracts. Production always requires V10. Test outcomes belong to the independent execution records and must not be inferred from this design document.

The first trigger-based implementation failed during an actual migration on MySQL 8.4.11 with the ordinary project account (HY000/1419, binary logging without SUPER). Its source and failed execution evidence are preserved separately. This replacement uses procedures and ordinary transaction privileges, with no trigger, extra grant or database security configuration change.
