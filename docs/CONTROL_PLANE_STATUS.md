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

Current status is `BOOTSTRAP_BASELINE_IN_PROGRESS`. The accepted commit is not
known until the initial commit is pushed and independently reviewed. Credentials
are intentionally absent from all control-plane files.
