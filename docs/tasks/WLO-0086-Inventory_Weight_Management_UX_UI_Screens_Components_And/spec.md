# Weight-management UX/UI inventory

Deliver an implementation-backed review map covering:

- every current core and supporting weight-management screen or transient surface;
- the feature-specific and shared UI components that must be assessed;
- every end-to-end user flow and its important alternate/error states;
- explicit exclusions for canceled, deferred, or unimplemented concepts;
- contract warnings that must be resolved during the review.

The maintained deliverable is `docs/tech/WLO-0086-WEIGHT-UX-UI-INVENTORY.md`.

## Acceptance criteria

- Screen inventory is cross-checked against navigation, F01, F06, F10, F13, Settings, and design-system implementation.
- Manual capture, trend, history, goal, body-composition, import, reminder, Hub, and portability flows are included.
- Loading, empty, error, sparse-data, accessibility, adaptive-layout, and destructive-action states are called out.
- Canceled and deferred concepts are clearly separated from current UI.
