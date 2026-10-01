# Impact persistence and business-key idempotency (SCRUM-62 / SCRUM-51)

The persistence slice uses main's M1 domain records and ports. `save` and the
`List<ImpactAnalysisRun>` return type of `findByChangeRequestId` are preserved;
findings are read in product-ID order.

The S3 candidate permits at most one analysis per change request. V6 adds a
unique constraint on `change_request_id` and backfills the stable idempotency
key from that ID, rather than a newly generated run code. V5 is unchanged so
its applied checksum remains valid. Existing duplicate change-request runs
cause migration validation to fail; this migration never deletes their history.
Such data needs explicit reconciliation before applying V6.

On a duplicate business key, the adapter signals `ImpactRunAlreadyExistsException`
with the committed winner. It uses a current locking read after the failed insert
so even an older MySQL repeatable-read snapshot can observe that winner. An ID or
run-code collision for another change request remains an ordinary duplicate-key
failure. The atomic workflow in #54 checks the existing run's rule set and replays
it without new findings, tasks or audit records; a different rule set conflicts.

Regression coverage exercises fresh run IDs/codes for the same change request,
unchanged findings/task linkage, independent changes, and direct SQL attempts
with a forged idempotency key. Existing M1/M5 domain tests are retained separately.
