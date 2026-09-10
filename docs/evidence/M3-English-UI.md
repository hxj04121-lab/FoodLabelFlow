# English UI validation

2026-09-09. Local-only update; no push or PR update.

Translated navigation, headings, cards, forms, validation messages, confirmation dialogs, accessibility labels, connection errors and help content into English. HTML language is en; display-time locale is en-GB. Canonical IDs, API keys and domain status codes are unchanged. Source data is not machine-translated.

Preserved the navy/violet theme, card hierarchy and responsive layout. Allowed longer English action groups and section headings to wrap, prevented icon shrinkage, and adjusted the search control width. Existing business integration remains intact.

Validation: TypeScript and Vite build passed. Fourteen functional tests (including live M1 read-only integration) passed, plus two English-specific tests for eight routes at 1440px/390px and form/confirmation content. Source scan found no Chinese characters in frontend/src. Desktop overview and mobile confirmation screenshots inspected. English tests ensure no Chinese visible content in their covered routes and no page-wide overflow. Existing bundle-size warning remains; no remote CI claim.
