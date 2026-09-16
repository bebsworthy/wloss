# Screenshot Compare

Open `index.html` directly in a modern browser. No installation, server, build or network required. Keep `compare.js` beside the HTML. Works with arbitrary mockups, web/mobile/desktop app captures and light or dark screenshots. Images are processed in memory and are never uploaded.

1. Load **Reference**, then **App / candidate** screenshots. Crop desktop chrome/frames beforehand if they differ.
2. Choose **Match widths** for a common screen width. This uniformly scales the candidate; it never stretches one axis. A single aligned character is not enough to determine scale.
3. Click the same landmark in both source previews to align it. Source previews scroll, so landmarks below the fold remain reachable. Alternatively enter source-pixel anchor coordinates and click **Align selected points**. Adjust scale and X/Y offsets numerically for fine alignment.
4. Use **Tinted foreground overlay** for the red-on-reference comparison. Auto chooses light/dark background by median luminance. Adjust cutoff and softness if panel backgrounds remain or faint text disappears. Original blend, pixel difference and side-by-side views are also available.
5. **Export PNG** saves the current composite at reference pixel resolution, regardless of preview zoom. Pixels outside the reference frame are clipped. Side-by-side output contains two reference-sized panels.
6. Save/load settings to repeat comparisons; screenshots are not embedded in the JSON, so reload the corresponding source images separately. Reset alignment leaves masking options intact.

## Limits

Masking is contrast-based, not OCR or semantic segmentation: it colors visible text, icons, dots, lines and sufficiently bright/dark filled areas. Mixed backgrounds or photographs may need separate crops. Original blend and difference views bypass masking. Difference uses original pixels at full opacity and also highlights antialiasing, changing values, and mismatched datasets; it does not score UI correctness. No screenshot capture or automatic landmark detection is included. Input images are limited to 24 megapixels each to bound browser memory usage.

## Verification

Run `node --test tools/screenshot-compare/compare.test.cjs` from repository root. These dependency-free tests check scaled anchor translation, dark/light masking, feathering, source transparency and automatic mode selection.

The browser flow was also checked in Chromium against actual Android and HTML screenshots: file loading, width matching, click anchors, numeric offsets, PNG download, side-by-side/difference dimensions, settings round trip and light masking. No JavaScript errors. Evidence is under `docs/tech/WLO-0111-evidence`.

## Agent workflow — use this instead of handing the UI to the owner

`agent.py` automatically captures a local HTML element and the current Android
screen, fits screen widths, finds visible first-glyph ink inside corresponding DOM/accessibility regions,
and writes overlay, aligned candidate, blend, difference, side-by-side, source
captures, browser/UI metadata, `report.json` and a browsable `index.html`.
No user interaction or manual screenshot processing is required.

Example (run from repo root; ensure the app is on the intended period first):

```sh
python3 tools/screenshot-compare/agent.py \
  --mockup docs/design/mockups/WLO-0103/weight-overview.html \
  --selector .phone \
  --serial emulator-5554 \
  --anchor-selector '#hero' \
  --anchor-text '77.1' \
  --click '[data-days="30"]' \
  --out /tmp/weight-comparison-01 --json
```

Generic file comparison (screen-origin alignment after fitting widths):

```sh
python3 tools/screenshot-compare/agent.py \
  --reference /path/reference.png --candidate /path/app.png \
  --out /tmp/comparison-01 --json
```

Use `--help` for selectors, viewport, masking and executable overrides. Paired
`--anchor-selector`/`--anchor-text` are optional; without them the screen origins
align. Anchors must be unique or capture fails explicitly. By default the tool aligns the first visible glyph’s top-left, excluding text-box
leading. Choose the same high-contrast first character in both regions, such as
the first 7 in the hero. The Android text must match the current visible value.
This is foreground-column detection, not OCR; touching characters may form a
single group. `--anchor-mode box` explicitly restores text-box alignment.
It does not rescale to fit the glyph: screen width determines scale so real
font-size differences remain visible. `--click` can be repeated to set browser state;
it does not mutate the Android app. The app must already show the desired state.
Different data and date periods are not silently inferred or corrected.

Dependencies: Python 3 + Pillow, Node + Playwright/Chromium, adb for live Android
capture. Uses normal Node module resolution / NODE_PATH, or the existing Codex
bundled Playwright runtime when available. No dependencies are auto-installed.
Outputs go to a **new** directory; existing directories are rejected to preserve
evidence. Partial captures remain available on failure. Exit codes: 0 success,
1 operational failure, 2 invalid arguments. `--json` emits a versioned result or
operational error envelope; argparse usage errors go to stderr.

Validation: `python3 -m unittest discover -s tools/screenshot-compare -p test_agent.py`.

For matching-data review fixtures, `--prepare-script /path/fixture.js` evaluates trusted local JavaScript in the captured mockup before clicks/capture. It changes only browser memory; the reference HTML stays untouched. Never pass an untrusted script. This supports matching prototype data to a known emulator dataset.
