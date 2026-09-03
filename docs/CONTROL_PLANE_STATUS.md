# Control plane status

The repository control plane is present under `.project-control/` and is bound
to PM execution contract SHA256
`2aafb403c3e437dd9f4d896c74504348f0aec50b7bdd0b171185605a0a1fdb41`.

Present artifacts:

- baseline and runtime state
- evidence index
- five S1 work orders
- S1 burndown and status
- merge queue metadata
- global lease policy

Current status is `LOCAL_BASELINE_VERIFIED_GITHUB_AUTH_SCOPE_GATE`. The local
bootstrap commit is
`5ef1e3f6caac144bd287934c246db75308e7e88e`; the accepted commit is not known
until the branch is pushed, independently reviewed, and merged. Credentials
are intentionally absent from all control-plane files.
