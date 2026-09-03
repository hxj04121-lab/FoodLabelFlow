# Stage 0 / Sprint 1 lease policy

The global bootstrap lease is held by the manager process until the baseline is
accepted. Shared files (Flyway migrations, Compose, CI, control-plane metadata,
and report evidence) are serialized through the bootstrap work order. Sprint 1
feature work must not start until the accepted baseline commit, green checks,
and the required independent approval are recorded.

No credentials or personal access tokens belong in this directory.
