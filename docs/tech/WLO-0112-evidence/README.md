# WLO-0112 — Agent-run capture and comparison

The owner wants the agent to generate comparisons, not hand them a manual UI.

Added `tools/screenshot-compare/agent.py` and its headless browser adapter. One non-interactive command captures the requested HTML element and current adb screen, measures a unique DOM and Android accessibility anchor, normalizes image widths uniformly, and generates red foreground overlay, blend, pixel difference and side-by-side PNGs. Sources, alignment metadata and UI hierarchy are saved with the output. It also supports arbitrary existing reference/candidate images. Errors and outputs are available as versioned JSON. Existing output directories are rejected.

Added discoverability to AGENTS.md: agents must run this workflow themselves for visual reviews and inspect the images instead of asking the owner to prepare them. CLI usage and dependencies documented in the tool README.

Validation: three Python tests passed (scaled anchors, anchor failure behavior, export geometry/masking), and two live captures completed successfully. Run-01 deliberately exposed a period mismatch after capture; inspection found app 90d versus browser 30d. Run-02 captures both at 90d. Datasets still differ (77.0 vs 77.1); reports explicitly warn that data/state equality is not inferred. No Android data or app code changed.

Use `run-02/index.html`, `run-02/overlay.png` and `run-02/report.json` for the completed demonstration. Anchor is the top-left of “Weight trend” text boxes, not a claim of glyph-baseline identity. Font rasterization and background masking artifacts remain distinguishable from layout differences.
