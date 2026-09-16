# Weight-goal safety contract (WLO-0080)

*Reviewed 2026-09-16. This is a WLO product-support policy, not medical
advice, diagnosis, screening, or a substitute for individual care.*

## Product boundary

WLO may record raw weight for anyone. Its generic automated goal, calorie,
milestone, and forecast-date system supports adults aged 18 and older only,
and only after the user explicitly answers the safety questions. A held or
unsupported result never deletes, hides, or disables the raw weight record.

The single code contract is `WeightGoalSafety.evaluate` in `:core:model`.
Goal UI must use the returned eligibility and neutral copy; the forecast
engine exposes a safety-gated entry point and may return bands only when
`allowsGoalMath` is true. Consumers must not recreate these rules or treat an
unanswered question as “no.”

| Situation | WLO product policy | Goal/date behavior |
|---|---|---|
| No goal chosen | Tracking-only is a complete state | No target or date is invented |
| Adult, screening answered “no,” supported inputs | Loss, maintenance, and gain are supported | Goal math may run inside the returned envelope |
| Under 18 | Adult goal engine unsupported | Hold targets, calorie advice, milestones, and dates; keep tracking |
| Pregnancy | Generic engine unsupported for this state | Hold generic targets and dates; point to maternity-care guidance |
| Breastfeeding | Generic engine unsupported for this state | Hold generic targets and dates; note that energy needs vary |
| Eating-disorder concern | Do not turn concern into a target or countdown | Hold goals and dates; keep neutral tracking and offer professional support |
| Medication/condition may influence weight | Generic model lacks the necessary clinical context | Hold targets and dates; keep tracking |
| Screening unanswered | Unknown is not “no” | Hold goals and dates until answered |
| Requested pace outside WLO range | Hard refusal, never a silent clamp | Return the supported maximum and require an explicit new choice |
| Plan below its configured energy floor | Hard refusal | Do not save it or forecast from it |

## Modes and WLO support envelope

- **Loss:** target must be below current weight. Maximum requested pace is
  the lower of 1.0% body weight/week and 0.9 kg/week. The absolute edge is the
  rounded upper end of CDC's “about 1–2 lb/week” gradual-loss range. It is a
  conservative app-support boundary, not a guarantee that a pace is suitable
  for a particular person.
- **Maintenance:** target is optional. If supplied, it must sit within WLO's
  ±1% maintenance band around current weight; requested pace is zero.
- **Gain:** target must be above current weight. WLO caps generic gain goals at
  0.5% body weight/week. Gain goals are first-class, but the current numerical
  integrator is loss-only; WLO therefore holds gain forecast dates until a
  direction-correct model is implemented and validated. NHS recommends gradual gain and suggests roughly
  300–500 additional kcal/day for adults, but does not prescribe a universal
  weekly weight rate; 0.5% is therefore explicitly a conservative **WLO
  product policy**, not a clinical threshold.
- Pace is a non-negative magnitude. Goal mode owns direction, avoiding the
  existing ambiguous mixture of signed and unsigned pace values.
- WLO enforces the configured plan floor supplied to the contract. The
  existing sex-based 1,200/1,500 kcal defaults are legacy product defaults,
  not universal medical cutoffs. NICE reserves 800–1,200 kcal diets for
  supported specialist strategies and diets below 800 kcal for limited,
  clinically assessed specialist use. WLO therefore never lowers or
  “overrides” a configured floor automatically.

## Evidence and rationale

- The US CDC describes gradual, steady loss as about 1–2 lb per week and notes
  that medicines, medical conditions, stress, genes, hormones, environment,
  and age can affect weight management: [CDC — Steps for Losing Weight](https://www.cdc.gov/healthy-weight-growth/losing-weight/index.html).
- NIH/NIDDK lists medicines and health problems that can materially affect
  weight and recommends reviewing them with a health professional rather
  than assuming a lifestyle-only cause: [NIDDK — Factors Affecting Weight & Health](https://www.niddk.nih.gov/health-information/weight-management/adult-overweight-obesity/factors-affecting-weight-health) and [Choosing a Safe & Successful Weight-loss Program](https://www.niddk.nih.gov/health-information/weight-management/choosing-a-safe-successful-weight-loss-program).
- NICE treats children and young people separately from adults and requires
  age/sex-appropriate energy and professional support rather than an adult
  self-directed deficit formula: [NICE NG246 — Physical activity and diet](https://www.nice.org.uk/guidance/ng246/chapter/Physical-activity-and-diet).
- Pregnancy weight gain is individualized by prepregnancy BMI and requires
  clinical judgment; inadequate as well as excessive gain can matter:
  [ACOG Committee Opinion — Weight Gain During Pregnancy](https://www.acog.org/clinical/clinical-guidance/committee-opinion/articles/2013/01/weight-gain-during-pregnancy).
- Breastfeeding energy needs vary with BMI, activity, and feeding pattern;
  CDC gives an additional 330–400 kcal/day as a general reference, which is
  incompatible with blindly reusing a generic deficit forecast:
  [CDC — Maternal Diet and Breastfeeding](https://www.cdc.gov/breastfeeding-special-circumstances/hcp/diet-micronutrients/maternal-diet.html).
- NICE says assessment/referral for a possible eating disorder should consider
  rapid loss, restrictive eating, disproportionate weight/shape concern,
  endocrine or gastrointestinal symptoms, chronic illnesses affecting diet,
  and more—not BMI alone: [NICE NG69 — Eating disorders: recognition and treatment](https://www.nice.org.uk/guidance/NG69/chapter/recommendations).
- NHS guidance recommends gradual weight gain and suggests adults may add
  around 300–500 kcal/day; sudden or unexplained loss and using food control
  to cope are reasons to speak with a GP:
  [NHS — Healthy ways to gain weight](https://www.nhs.uk/live-well/healthy-weight/managing-your-weight/healthy-ways-to-gain-weight/).

## Copy rules

Copy names what WLO can and cannot calculate. It never labels a person,
diagnoses a condition, calls a body “unsafe,” promises that an allowed pace is
safe, or uses urgency/shame. A refusal says that raw tracking remains
available, that nothing was silently changed, and—where relevant—that a
qualified professional can provide an individual plan.

## Implementation hand-off

WLO-0081 should collect the four explicit non-diagnostic answers and use this
result in first-run/goal editing. WLO-0073 should require the same result before
producing forecast bands or milestone dates. Historical imported targets that
cannot pass the contract remain history and never become active automatically.
