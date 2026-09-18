# Requested mockup comparison — 2026-09-18

Captured the currently installed app with `tools/screenshot-compare/agent.py` against
`docs/design/flows/10-intake-target-f01.html`. Inspected side-by-side, foreground
overlay and report.json. Reference is left, Android is right.

## Findings

1. **Hero typography is smaller in Android.** After screen-width normalization,
   the first maintenance glyph is approximately 19.5 px high versus 25 px in the
   mockup (about 22% shorter). This is a glyph measurement, not a claimed font-size
   measurement. The adjustment input also has less visual emphasis.
2. **Vertical hierarchy is compressed.** The projection, capsule, input, slider and
   explanation move progressively upward relative to the mockup. The header has
   a separate raised surface and larger title instead of the quieter mockup header.
3. **Source presentation differs.** Android places source chips before the units;
   the mockup places them below. Generic “derived” loses the more useful
   “Starting suggestion” / “Set by you” distinction. The target-weight context
   also loses the mockup's gold emphasis.
4. **Controls differ visibly.** Selected capsule is blue rather than green; the
   native slider has a much thicker track and tall thumb. Standard M3 components
   should remain; documented styling parameters should be evaluated for matching.
5. **Actions differ.** Android adds a bottom Cancel and narrower “Save intake”
   button. The mockup uses one full-width Save and a disclosure row with a plus;
   Android renders the explanation as a green text button.

## Comparison limits

Only reference headline values were adapted in browser memory for same-glyph
alignment: 1,776 maintenance, 1,276 intake, 77.1 → 74 kg context. The original HTML
was not changed. The reference chart remains its illustrative 84 → 78 kg example;
Android uses the production forecast for 77.1 → 74 kg. Curve geometry and dates
are therefore not a visual-parity test. Android is showing a saved weekly-average
intake while the reference shows its daily starting-suggestion state.

The comparison establishes layout/type/control differences. Functional completion
does not establish visual fidelity; the app is not a close visual match yet.
No app or mockup code was changed for this review.
