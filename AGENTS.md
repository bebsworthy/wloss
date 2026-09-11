# WLO ("wloss") — Android weight-loss companion

## What this is

WLO is an **open-source, completely free, local-first Android weight-loss
companion** for a numbers-geek audience. All user data lives on the device:
no account, no server, no SaaS, no paywall, no ads — the project is unpaid,
so there is no backend. AI runs **on-device by default** (model zoo bundled
or downloaded); any cloud AI is strictly **BYOK** (the user's own API key)
and gated by **per-capability consent** — each AI category (food-photo,
voice-input, meal-planning, silhouette, poop-photo, insights-chat) has its
own independent opt-in toggle. Every derived number carries provenance and a
"how we got here" explainer; weak data is *held*, never guessed. Design bar:
minimalist, dense with stats, rich micro-interactions, zero guilt/shame copy.

Flagship differentiators (from competitive research): on-device adaptive-TDEE
engine with a 3-band decelerating goal forecast; photo-first logging with an
editable-before-save correction loop; plan→shopping-list pipeline with pantry;
the meal-plan↔poop/fiber correlation (exists in no competitor); silhouette
tracking via **vector outlines only — body photographs are never stored**
(owner ruling R-U16); and "your data outlives the app" portability.

## Documentation layout

```
docs/
├── objective.md            Product objective: principles, FOSS/free-forever
│                           model, per-capability AI consent, feature set
├── research/               Competitive analysis (Phase 0, Sep 2026)
│   ├── synthesis.md        ★ START HERE: market map, 4 feature buckets,
│   │                       cross-cutting lessons, v1 checklist
│   └── <app>.md            18 app deep-dives + poop-tracker category study
│                           (myfitnesspal, cronometer, macrofactor, zolt,
│                           cal-ai, snapcalorie, foodvisor, mealime, paprika,
│                           hevy, fitbod, happy-scale, methreesixty, gyroscope,
│                           waistline, openscale, …)
└── features/               Functional specifications (Phase 1)
    ├── FEATURES.md         ★ Master doc: feature map F01–F13, data spine,
    │                       Day/Week loops, consent matrix, frozen rulings
    │                       (R-*), per-spec reviews, v1 scope, open questions
    ├── TEMPLATE.md         Template every feature spec follows
    └── F01…F13-*.md        13 feature specs (onboarding-diet-plans, food-
                            logging, meal-planning, shopping-pantry, exercise,
                            weight-body-metrics, energy-engine, silhouette,
                            gut-tracker, daily-hub, insights-gamification,
                            ai-platform, data-vault)
```

Reading order for newcomers: `docs/objective.md` → `docs/features/FEATURES.md`
→ the feature spec you're working on → `docs/research/synthesis.md` for the "why".

**Governing docs:** `FEATURES.md` §3 rulings (R-B*/R-A*/R-U*/R-C*/R-S*) are
frozen cross-feature decisions — do not contradict them in feature docs or
code; amend the master doc instead. `docs/tasks/` is the ticket CLI's mirror
(see below); never hand-edit task state there.

<!-- ticket:skill:begin (v1) -->
## Ticket — task tracker

This project uses `ticket` (CLI). Track work there, not in chat:
`ticket task <ID>` to read · `ticket task new <title>` to create ·
`ticket task <ID> update` (stdin JSON) to change · ask humans via
`ticket task <ID> question add` (JSONL stdin). Markdown by default, `--json` if
you must parse. Full help: `ticket --help` and every subcommand.
<!-- ticket:skill:end -->
