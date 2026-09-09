# Frontend stack decision

Date: 2026-09-07
Status: Local frontend migration and dashboard design implemented; team integration and business API acceptance pending.

Delivery branch: XFY. The member's S1 work order now records the selected stack and its pending-team-integration status. The original PM hash is retained solely as baseline identity. XFY pushes do not match the current CI push branch filter; a PR targeting main would trigger the existing PR workflow. No shared CI changes are included.

The target frontend stack is React + TypeScript + Tailwind CSS + shadcn/ui. Retain Vite, React Router, the Spring Boot REST boundary, and the S1-M3 business acceptance criteria. Tailwind supplies styles; shadcn/ui supplies editable component source.

The existing Ant Design shell is the migration source, not the target design. Migration includes dependencies and lockfile, Tailwind/Vite integration, shadcn configuration and aliases, shared theme CSS, replacement of App/HealthPage components and icons, and removal of unused Ant Design imports and reset styles. Revalidate the TypeScript build, health API integration, keyboard interaction and browser paths afterward.

Local migration and browser smoke are recorded in `docs/evidence/M3-dashboard-design.md`. This is not S1 business acceptance: Catalog/Formula data is an explicitly labelled read-only seed snapshot, creation/publication are unavailable, and future workflow pages show unavailable states. No team approval, PR, remote CI or remote work-order update is asserted. Historical Stage 0 evidence remains unchanged. The signed PM snapshot and its hash must not be silently rewritten; reconcile the team contract and work-order wording through the team's change process.

Detailed local task and resource guidance is maintained in the workspace-root Xu_Feiyang_SpecTrace_Task_Analysis.md and Frontend_Design_Resource_Guide.md.
