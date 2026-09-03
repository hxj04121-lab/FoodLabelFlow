# Jira bootstrap delta

Target site: `https://hxj04121.atlassian.net`
Target project: `SCRUM` (displayed as TEAM 16)

The authenticated Jira UI was used to create the Stage 0 item and the five
Sprint 1 work items. All six were created as `Feature` with the default `Idea`
status. The descriptions carry the PM hash, branch, work-order scope,
acceptance criteria, dependencies, and the current human-gate state.

## Created items

| Scope | Jira item | Status | Assignee |
|---|---|---|---|
| Stage 0 bootstrap | [SCRUM-6](https://hxj04121.atlassian.net/browse/SCRUM-6) | Idea | 黄翔嘉 |
| S1-M1 supplier/material/formula main slice | [SCRUM-7](https://hxj04121.atlassian.net/browse/SCRUM-7) | Idea | 黄翔嘉 |
| S1-M2 supplier/material architecture contracts | [SCRUM-11](https://hxj04121.atlassian.net/browse/SCRUM-11) | Idea | Unassigned |
| S1-M3 web shell/catalog/formula UI | [SCRUM-8](https://hxj04121.atlassian.net/browse/SCRUM-8) | Idea | Unassigned |
| S1-M4 identity/RBAC/workflow | [SCRUM-9](https://hxj04121.atlassian.net/browse/SCRUM-9) | Idea | Unassigned |
| S1-M5 integration test/API support | [SCRUM-10](https://hxj04121.atlassian.net/browse/SCRUM-10) | Idea | Unassigned |

The Jira account exposed only the current user (`hxj04121@gmail.com`) and
Unassigned in the assignee picker. M2–M5 therefore remain unassigned rather
than being assigned to unverified accounts; their intended owners remain in
the item descriptions and source YAML work orders.

The items are not linked to an accepted baseline commit yet because PR #1 has
no independent approval and has not been merged.
