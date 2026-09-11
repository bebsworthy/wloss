# <Feature Name> — Functional Specification

> **How to use this template:** every feature doc in `docs/features/` follows this
> structure exactly. Write blue-sky where invited, but stay concrete: a reader should
> finish the doc knowing what the feature does for the user, how it behaves, and how
> it plugs into the rest of the app. Reference other features by ID (`F07`), never by
> guessed titles.

---

## Identity

| | |
|---|---|
| **Feature ID** | F__ — <Name> |
| **Provides** | One sentence: the service this feature delivers |
| **User problems solved** | 2–4 bullets, from the user's seat |
| **AI consent category** | Owns <category> consent toggle / consumes F12 toggles / none |
| **Primary evidence** | Links into `docs/research/*.md` sections that justify this design |

## 1. Purpose & Core Objectives

- What this feature is *for*, in one paragraph.
- Core objectives — ideally phrased so they can later be verified (e.g. "a meal is
  logged in ≤5 s with zero mandatory text input").
- The benefit to the user, and the benefit to the *other* features (why the app
  needs this feature to exist).

## 2. User Moments — when and how it is used

- Cadence: what happens many times a day / daily / weekly / episodically.
- The physical context (kitchen, gym, bathroom, couch) and emotional context.
- The single most common flow, described end-to-end in 3–5 steps.

## 3. How It Works — functional mechanics

- Inputs (from user, from other features, from device sensors).
- Processing, including any AI: **on-device by default; state exactly which F12
  consent category a cloud call would need, and what degrades when consent is off
  or there is no network.**
- Outputs and artifacts produced (records, scores, lists, images, exports).
- State the feature owns and is responsible for.

## 4. User Interaction Model

- Entry points: where in the app the user reaches this feature (F10 Daily Hub,
  F11 Insights, notifications, widgets…).
- Primary flows, step by step (happy path + the top 2 error/fallback paths).
- **Input minimization:** enumerate what the user never has to type, and what the
  one-tap/zero-tap shortcuts are.
- **Micro-interactions:** the haptic, motion, animation and sound moments that make
  it feel alive (be specific: what animates, when, how long, what it communicates).
- Data-quality gating: what the feature refuses to claim when data is thin.

## 5. What the User Gets Out

- The numbers, lists, images or narratives this feature surfaces.
- **Provenance rule:** every derived number states measured / estimated / derived
  and links to a "how we got here" explainer.
- Visualizations owned by this feature (chart types, ranges, annotations).

## 6. Motivation & Psychology

- Why the user returns to this feature; what itch it scratches.
- Gamification elements this feature *owns* (streaks, PRs, badges — coordinate with
  F11, which owns the system).
- Tone rules for anything the feature says to the user (no guilt, no shame — the
  category's most-cited complaint).

## 7. Relations to Other Features

- **Consumes from:** F__ — what and why.
- **Feeds into:** F__ — what and why.
- Shared concepts it relies on (Targets, Logs, Consent, Provenance, Trend weight…).
- Conflict/boundary notes: where this feature ends and a neighbor begins.

## 8. Blue Sky Ideas

Tagged ideas, ordered by ambition — mark each `[v1]`, `[v1.x]`, `[future]`,
`[moonshot]`. Blue sky is wanted here; separate the buildable from the dreamy.

## 9. Guardrails, Privacy & Sensitivity

- Sensitivity of this feature's data and the protections it requires
  (biometric lock, hidden gallery, redaction before any cloud call).
- Hard rules this feature must never break (from the objective: free forever,
  no SaaS, no account, per-capability consent, local-first).

## 10. Open Questions

- Decisions deliberately deferred, with the options as currently framed.
