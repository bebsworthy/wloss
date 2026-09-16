# Weight-gain forecast v1 — evidence and product boundary

## Decision

Eligible adult gain goals use a dedicated dynamic energy-balance path. At each
weekly step, the model raises expenditure by **22 kcal/day per kg gained** from
the starting measured or formula TDEE, subtracts that from the planned intake,
and converts the remaining positive surplus with WLO's frozen partition-free
7,700 kcal/kg product constant. The three bands vary the rate scale; all stop at
the same finite 260-week horizon. A zero/negative surplus or a target beyond the
surplus-defined equilibrium gets no date.

This is intentionally not the loss integrator with its signs reversed. Loss
keeps WLO's calibrated BMR/Adjustment/Activity path. Gain uses an explicit
expenditure-feedback equation and is versioned as
`forecast/directional-3band-v2`.

## Primary evidence

- Hall et al.'s peer-reviewed dynamic adult body-weight model makes expenditure
  depend on changing fat and lean mass, thermic effect, adaptive thermogenesis,
  and mass-scaled activity; it was checked against independent human feeding
  studies. Its linearized long-term form defines an effective energy density and
  an expenditure response to body weight. [NIDDK peer-reviewed appendix](https://www.niddk.nih.gov/-/media/Files/BWP/Hall_Lancet_Web_Appendix.pdf)
- Hall and Chow publish average linearized parameters of **9,100 kcal/kg** and
  **22 kcal/kg/day** and explain why repeated weight observations and explicit
  uncertainty are necessary for individual free-living estimates. WLO adopts
  the 22 kcal/kg/day feedback but retains the governing R-A6 product choice of
  7,700 kcal/kg rather than silently changing the existing loss model.
  [American Journal of Clinical Nutrition primary paper](https://pmc.ncbi.nlm.nih.gov/articles/PMC3127505/)
- Controlled overfeeding evidence reports large person-to-person variation and
  rising expenditure during overfeeding, mainly from tissue-deposition costs
  and maintaining a larger body. This supports deceleration and wide bands; it
  does not justify a precise body-composition claim.
  [British Journal of Nutrition review of overfeeding studies](https://pmc.ncbi.nlm.nih.gov/articles/PMC1543621/)

## Assumptions and limitations

- Adult, safety-screened goals only; `WeightGoalSafety` remains the only
  eligibility gate. Pregnancy, breastfeeding, eating-disorder concern,
  medically influenced weight, unsupported pace, and incomplete screening
  continue to withhold all goal math.
- Planned intake and baseline activity are held constant. The model does not
  claim how much gain is muscle, fat, glycogen, or water and does not prescribe
  a surplus.
- The first weeks of overfeeding can include glycogen, sodium, and water shifts
  that this long-term scalar model deliberately omits. Trend weight and wide
  ranges are therefore required.
- The date is a product estimate, not medical advice or a promise. Measured mode
  becomes available only through the existing data-quality release boundary;
  developing mode continues to suppress its central point date.

## Executable checks

`GainForecastTest` covers gated cold-start and measured forecasts, monotonic
gain, ordered bands, decelerating rates, already-met targets, zero surplus,
unreachable equilibrium targets, and finite horizon termination. Existing loss
goldens and calibration benchmarks remain unchanged apart from the model-version
stamp.
