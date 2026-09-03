# Jira bootstrap delta

Target site: `https://huangxiangjia.atlassian.net`
Target project: `SCRUM`

Direct Jira synchronization was not available in the current local tool
context. This exact delta is ready for an authenticated project administrator
to apply; no issue IDs are invented.

## Project bootstrap actions

1. Confirm or create the SCRUM project under the agreed project owner.
2. Create the Stage 0 bootstrap item for repository
   `hxj04121-lab/FoodLabelFlow`, branch `chore/stage0-bootstrap`, and PM
   contract SHA256
   `2aafb403c3e437dd9f4d896c74504348f0aec50b7bdd0b171185605a0a1fdb41`.
3. Create five S1 work items and assign them to M1 Huang Xiangjia, M2 Cai
   Runchen, M3 Xu Feiyang, M4 Zhu Wenyu, and M5 Sun Huajian according to the
   YAML files under `.project-control/work-orders/S1/`.
4. Link the five S1 items to the accepted Stage 0 baseline commit after the
   PR is independently reviewed and merged.
5. Attach DB0, CI/security, local smoke, and human acceptance evidence URLs
   only after those runs exist.

## Non-fabrication note

Because no authenticated Jira write was performed, there are no claimed Jira
issue keys, status transitions, estimates, worklogs, or assignee changes in
this bundle.
