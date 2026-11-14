## Context

Four small-but-real v1 owner calls left after the 2026-09-11 review
pass, batched here because each is a single decision with a clear
recommendation.

## Q1 — F05 cardio depth in v1

Minimal native cardio entries (type/duration/effort/distance) + Health
Connect import (recommended), or native pacing/zone charts from day one?

## Q2 — F13 attachments in backup bundles

R-U5 settled encryption (default on, passphrase, rotation 7). Are photo
attachments inside bundles by default (explicit per-bundle opt-out), or
excluded by default (explicit per-bundle opt-in)? Recommendation:
excluded by default — smaller backups, matches R-U14's photos-default-off
posture; the storage dashboard already governs on-device copies.

## Q3 — F01 onboarding success metric

Define "active plan" for the <3-minute objective's verification:
plan written, or plan written + first log within 24 h? Recommendation:
the stricter latter — it measures the loop, not the wizard.

## Q4 — F02 recognizer training-data policy

F02 §10 calls this "a project-level decision before model v1": which
datasets/licenses are acceptable for the shipped food recognizer?
Recommendation: OSI-/CC-licensed open datasets only (e.g. open
food-image corpora), each named on the F12 model card; no proprietary
training data anywhere in the zoo.

## Acceptance criteria

1. Each decision recorded where its spec consumes it (F05 §3, F13 §3,
   F01 §1, F02 §9/F12 §3.2) — as inline notes or new R-* rulings.
2. Corresponding §10 open questions annotated resolved.
