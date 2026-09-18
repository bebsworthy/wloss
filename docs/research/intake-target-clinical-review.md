# Intake target suggestions — clinical evidence review

Reviewed 2026-09-18 · WLO-0125 · Focused source review, not a systematic review.
This records evidence and proposed product implications, not a change to frozen
rulings or the active mockup. The earlier suggested −15%/+10% defaults were
product hypotheses; the reviewed sources do not validate those exact rules.

## Findings

### Starting deficits: supported, with a defined population

The [2013 AHA/ACC/TOS guideline, recommendation 3a](https://www.ahajournals.org/doi/pdf/10.1161/01.cir.0000437739.71477.ee?download=true)
offers a 500 or 750 kcal/day deficit as one dietary strategy for adults with
overweight/obesity who would benefit from weight loss, in comprehensive lifestyle
care. It also lists individualized intake bands of 1,200–1,500 kcal for women
and 1,500–1,800 for men. These bands are not universal safety thresholds.
The linked [Academy summary](https://www.andeal.org/template.cfm?template=guide_summary&key=4187)
reproduces that recommendation and emphasizes nutrition adequacy and individual context.

The [1998 NHLBI report](https://www.ncbi.nlm.nih.gov/books/NBK2009/)
is the source for the older 500–1,000 kcal deficit and 1–2 lb/week guidance.
Do not merge these different documents into a timeless global standard.

### Expected outcomes: the supplied summary overstates certainty

[PMC3447534 is Finkler, Heymsfield and St-Onge (2012)](https://pubmed.ncbi.nlm.nih.gov/22717178/),
“Rate of weight loss can be predicted by patient characteristics and intervention
strategies.” It is not an NIH guideline. Its analysis of 35 studies reports that
the conventional expected rate is rarely achieved. Duration, starting weight,
age, prescribed restriction and counselling frequency affect results. Its
completers-only and study-level analysis limits individualized prediction.

The 2013 guideline's evidence synthesis reports 4–12 kg loss at six months
across dietary interventions, not a guaranteed weekly linear rate.

### Dynamic prediction: supported; automatic repeated calorie cuts are not

[Thomas et al. (2014), PMC4035446](https://pmc.ncbi.nlm.nih.gov/articles/PMC4035446/)
is a Journal of the Academy of Nutrition and Dietetics article comparing
prediction approaches, not the original Lancet NIH simulator paper.
[Hall et al. (2011), Lancet](https://pubmed.ncbi.nlm.nih.gov/21872751/)
is the relevant original dynamic modelling research.

Both support changing expenditure and nonlinear trajectories. Changes in body
composition, water/glycogen, food thermogenesis, movement and adaptive responses
make fixed kcal-to-weight conversion unreliable as a long-horizon predictor.
Lower expenditure from a smaller body is not synonymous with adaptive
thermogenesis beyond that expected change. A plateau alone does not establish
a specific physiological cause or justify an automatic target reduction.

### Low intake is different from a large deficit

Use [current NICE NG246 §1.16.8–12](https://www.nice.org.uk/guidance/ng246/chapter/Physical-activity-and-diet),
not a legacy review-protocol appendix. Its categories refer to total intake:
800–1,200 kcal/day is low-energy and under 800 is very-low-energy. These require
specified supported care; the latter additionally requires a clinically assessed
need for rapid loss. Diets must be nutritionally complete, supervised and limited
to no more than 12 weeks. Therefore an 800-kcal deficit is not a VLCD definition.
A 1,200-kcal app default is not automatically endorsed by modern NICE guidance.

### Gain needs separate evidence

[NHS healthy weight-gain guidance](https://www.nhs.uk/live-well/healthy-weight/managing-your-weight/healthy-ways-to-gain-weight/)
suggests adults could add about 300–500 kcal/day for gradual gain. It does not
establish an optimal surplus for every user or validate a mirrored loss algorithm.

## Implications for WLO — proposals, not adopted policy

- Withdraw the exact −15%/+10% rule as a literature-based recommendation.
- For a supported adult matching the overweight/obesity guideline population,
  500 kcal/day is a more directly sourced starting-deficit candidate than 15%.
  Choosing the lower end is still WLO policy, not proof of a personalized optimum.
- Maintenance uses the best supported expenditure estimate without an intended
  offset. Gain could use a separately justified 300-kcal starting candidate;
  do not extrapolate obesity-treatment evidence to gain or normal-weight loss.
- Eligibility, uncertainty, intake floors and supported goal bounds must be
  evaluated before offering any number. If a candidate fails, offer an explicit
  supported alternative or hold; no silent floor reduction or automatic save.
- Show the actual rounded maintenance, target and difference, their method and
  population limits. A clinical starting strategy does not guarantee an outcome.
- Adaptation needs sufficient intake/trend evidence, with user-approved changes.
  A scheduled review is not a scheduled calorie cut.
- Resolve the conflict between legacy floor defaults and current low-energy
  guidance in FEATURES.md before shipping automated suggestions. Also review
  R-A1/R-A6: 7,700 kcal/kg remains an approximation, not a clinically validated
  universal conversion or an implementation of the Hall model.

No personal age/sex/weight/exercise questionnaire is needed to complete this
product literature review. Implementation and mockup numbers remain unchanged.
