# MeThreeSixty — Feature Analysis

**Category**: Health & fitness — smartphone 3D body scanning / body-composition and measurement tracking (weight-loss companion)
**Platforms**: iOS (iPhone; App Store release March 8, 2020; version 5.3.1 as of Aug 2026), Android (Google Play). No web or wearable app. iOS app is ~343 MB.
**Pricing model**: Free core app + "MeThreeSixty Premium" auto-renewing subscription (introduced v4.0, Aug 2024). Premium unlocks full scan history, "scan results within seconds, not minutes", exclusive metrics (BMI, Fitness Index), and unlimited scanning. User reviews cite ~$30/year ("Paid 30 bucks", "$30+tax").
**AI usage**: Computer-vision pose detection (AI-based, added v3.0, 2021) that guides the user into stance; body-mesh and measurement estimation from two photos; "FutureMe" projected-body rendering. No conversational/LLM coach in the product.
**Local & privacy posture**: Marketed as privacy-first: "your scans stay on your device and are never uploaded to the cloud… the app scans your silhouette, not your image" (App Store description). The Size Stream privacy policy is more nuanced: photos are processed into "Body Scan Data" (photos "not stored… once we have processed them"), Body Scan Data is treated as collected personal information / biometric information under CCPA, targeted advertising is used unless opted out, and a Washington My Health My Data / Nevada / Connecticut Consumer Health Data notice exists. The app also tracks medication "Dosing Information" (GLP-1 era features). Not HIPAA-regulated; children under 18 excluded.

## Overview

MeThreeSixty, by Size Stream LLC (Cary, NC), turns a smartphone camera into a 3D body scanner. Size Stream's core business is industrial 3D body measurement (scanner hardware/platform licensed for apparel, fitness, telemedicine, clinical studies); MeThreeSixty is its consumer face. It launched in March 2020 as "Size Stream @ Home", originally requiring a scan suit/kit and a second person to capture; the 2020 v2.0 update reduced a scan to just two photos (front + side). The company claims "nearly 1 million people around the world" use it, and the US App Store listing shows a 4.75-star average across ~18,400 ratings — a large, genuinely engaged base for a body-scanning app.

The core pitch is explicitly anti-scale: "Weight is a one-dimensional number and doesn't give you the whole story." From two photos it builds a 3D avatar, extracts 14+ circumference measurements (waist, hips, chest, arms, etc.), and estimates body-composition metrics (body-fat %, visceral fat, lean mass, fat mass index, waist-to-hip ratio, body surface area). It positions itself as "a smarter alternative to DEXA scans" — no clinic, no radiation, no cost per scan. Its target user is the weight-loss journey consumer (with a visible GLP-1 angle: a sister brand me360rx sells GLP-1 access, and the app added medication/dose tracking).

Market position: it is the de facto mainstream phone 3D-body-scan app (a wave of 2024-2026 imitators — Recomp AI, Spren, Body Snap, Contura — cluster around it in App Store search). Its differentiation vs those rivals is the full 3D avatar + scan-comparison experience and the Size Stream measurement pedigree, whereas Spren/Body Snap compete on body-fat accuracy claims and cloud reports. For WLO it is the single most relevant reference for silhouette-tracker mechanics and capture UX.

## Feature inventory

### Photos / silhouette / body scanning (CRITICAL)

- **Capture ritual — two poses only**: front-facing and side-facing photos. "Just two quick poses. Front and side. That's it." The app builds a "personal 3D body avatar" from those two images (v2.0, Nov 2020, eliminated the earlier scan suit + helper person).
- **Live pose alignment**: v3.0 (Mar 2021) introduced "AI-based pose detection [that] guides you into the correct stance with visual and audio clues" — enabling scanning by yourself. Later versions added an instruction video and "better body positioning guidance" (v3.4.2) and voice instructions (v3.2.0). Reviews describe a stick-figure overlay: "the green and red stick figure lines bounce everywhere" — i.e., a live skeleton that turns green when a body segment is correctly placed. Scan start is gated on all regions being valid ("Even when the little boxes were both green, the scan refused to start" — one reviewer).
- **Environmental guardrails** (from the official FAQ list): form-fitting clothing required ("baggy or loose clothing cause measurements to be too large"), hair tied up, clean/clutter-free background ("clutter in the background can make it hard to complete a scan"), no direct sunlight, no other people/moving objects (TV, fan) in frame, sufficient floor space, minimum camera resolution, no tablets, glasses/beard generally OK, pregnancy excluded.
- **Estimation output**: 14+ circumference measurements; body-fat % (with a published "Size Stream Body Fat Formulas" methodology and a web Body F.A.T.% calculator that mirrors the app's tech using 4 tape measurements); visceral fat; lean body mass; fat mass index; BMI; waist-to-hip ratio; body surface area; and a proprietary "Fitness Index" (single value combining muscle mass, body measurements, and body-fat %). A "healthy range for body-fat percentage" explainer is built in. Users can query/adjust individual measurements manually (an FAQ covers manual updates, though reviewers complain the scan result cannot simply be overridden).
- **Progress comparison UX**: a scan history with side-by-side 3D avatar comparison; a user-chosen "reference scan" baseline for comparisons; "FutureMe" — "a data-powered preview of your aspirational self," implemented as a weight-delta slider on your avatar (e.g., "-15lb") showing a projected body. Note the FAQ troubleshoots a missing Future-Me slider, and one reviewer reports rendering artifacts ("the -15lb slider makes the skin go all wrinkly and the limbs change length").
- **Processing model**: free tier renders scans "within minutes"; Premium "within seconds." The FAQ includes "How long does it take for my scan to upload?" and "I don't have an internet connection. Can I still scan?", suggesting some server involvement despite the on-device marketing; the privacy policy states photos are not retained after processing into Body Scan Data.
- **Privacy details**: marketing line is "scans your silhouette, not your image… photos never uploaded to cloud." Policy says photographs are not stored once processed; Body Scan Data is shared with third parties only with explicit consent; deletion of scans and of the account are supported in-app (FAQ items). One 2025 reviewer reports "Apple Privacy Report… reveals this app phones home to Facebook.com," indicating third-party SDK traffic inconsistent with the privacy-first framing. There is a dedicated Washington Consumer Health Privacy Policy (My Health My Data Act compliance), notable as a state-consumer-health-data pattern.

### Data sources & integrations

- Apple Health sync (added v3.5.0, 2022) — weight and body metrics.
- Manual inputs: height, weight (weight "inputted" by the user; FAQ covers weight-display mismatches), goals, measurement-type preferences; preferences persist to cloud (v2.1.0).
- 3D export: an FAQ covers accessing your 3D data outside the app (Size Stream B2B pipeline; not a general-purpose open export in the consumer tier) and even paid 3D prints of your scan.
- No Google Fit / Health Connect integration documented; no wearable, scale, or nutrition integrations. It is a single-purpose scanner, not an aggregator.

### Statistics, visualization & gamification

- Customizable dashboard of "measurement tiles"; trend views per measurement over past month / 3 months / full year (App Store "TRACK WHAT MATTERS TO YOU").
- Weight Trends with a moving average "to see consistent progress every day" (added with v4.0, Sep 2024).
- Visualization is avatar-centric: 3D viewer, spin/inspect, side-by-side scan diff, FutureMe slider.
- Gamification: N/A — not offered. No streaks, levels, badges, or challenges. Motivation is carried entirely by the visual "see your body change" loop (users repeatedly cite this as what keeps them going: "it proves it!" — a user down 119 lbs).

### AI features (on-device vs cloud)

- Pose-detection CV during capture (described by Size Stream as AI-based; runs live on the phone).
- Body-mesh reconstruction + measurement/body-fat inference from 2 photos (proprietary Size Stream model; marketed as on-device, policy suggests processing with server involvement; the FAQ's offline-scan question is answered around this ambiguity).
- FutureMe body projection (rendered morph of the avatar).
- No LLM/chatbot/coach features; no generative AI. Developer replies to store reviews are reportedly LLM-templated (a 3-star review mocks this), but that is support tooling, not a product feature.

### Input-minimization techniques

- The scan itself replaces 14+ tape measurements and a gym visit — the app's entire value proposition is "photo over manual entry."
- Only truly necessary manual inputs remain: height once, weight (optionally from a smart scale via Apple Health), goal weight.
- Voice + visual coaching during capture removes the need for a second person ("measure by yourself!").
- Body-fat calculator web tool reuses the same formulas with 4 tape numbers as a zero-friction lead magnet.

### Design & UX / micro-interactions

- Utilitarian health-app design: avatar viewer, tile dashboard, standard charts. Clean but not a design benchmark; press coverage focuses on the tech, not the aesthetics.
- Micro-interactions exist mainly in the capture flow (skeleton overlay snapping green, audio cues) and the FutureMe slider — arguably the two most delightful moments.
- Friction points reported by users: forced app-version updates that block launch ("no longer supported… the app is already up to date"), OTP login delays, review prompts mid-exploration, upsell prompts (supplements/GLP-1), and losing scan access behind Premium.

## Strengths & differentiators

1. **Two-photo simplicity**: the capture ritual shrank from scan-suit-plus-buddy (2020) to two solo poses (2021) — the best-in-class minimal capture among body-scan apps.
2. **Silhouette-not-photo privacy story** (marketing): strong, memorable framing for camera-based body data; backed by a state consumer-health-data policy apparatus (WA MHMDA etc.).
3. **The 3D avatar as progress artifact**: a body you can rotate and compare side-by-side beats both photos and tape measures for showing "real progress the scale hides" — the single most-praised feature in positive reviews.
4. **Industrial measurement pedigree**: Size Stream's B2B 3D measurement tech lends credibility ("smarter alternative to DEXA"), plus published body-fat formulas.
5. **Reference-scan baseline + FutureMe projection**: baseline-diff and goal-body preview are sophisticated motivational mechanics rarely seen elsewhere.
6. **Scale of validation**: ~18.4k US ratings at 4.75 — exceptional for the category; multi-year users report real weight-loss outcomes (one cites 119 lbs).

## Weaknesses & user complaints (cited from App Store user reviews via iTunes RSS, 2025-2026)

- **Accuracy skepticism** is the dominant 1-star theme: "average discrepancy ~1.47in per measurement" vs a tape; "completely made-up numbers"; "measurements change randomly, or get worse"; "wildly different than tape measurements"; a long-time user says the avatar "looks nothing like me… some generic woman avatar."
- **Capture fragility**: "very difficult to get the scanning pose correct"; false clutter detection even "against a bare white wall"; "now it can't see my feet"; scans stuck in processing; total scan failure for some users across lighting/clothing variations.
- **Data loss & account problems**: multiple reports of scans vanishing after updates or logout ("scans from the last couple years" gone; "took my $$$ and deleted all previous data"), OTP codes expiring before arrival.
- **Paywalling personal history**: "I now have to pay for a Premium plan just to view my past scans? Ridiculous!" — gating a user's own previously-free scan history behind subscription is a recurring anger point.
- **Forced-update kill switch**: several users locked out by in-app version checks even when up to date; "have to update every single time I use it."
- **Privacy dissonance**: "Apple Privacy Report… reveals this app phones home to Facebook.com" vs the "never uploaded to the cloud" marketing; plus supplement/GLP-1 upsells undermining the health-trust position.
- **Demographic fit**: "Not optimized for women or people past 40" — post-50 users report the model "assumes any weight loss is muscle loss," ignoring strength training and protein intake.
- **No meaningful editing**: users beg for the ability to correct scan measurements manually; tape-vs-scan mismatch cannot be reconciled in-app.

## What WLO should learn

1. **Steal the capture ritual, not the tech stack**: front + side photos, a live pose skeleton that must turn green per body segment, voice coaching, and explicit pre-scan checks (clothing, hair, clutter, lighting, distance). WLO can implement exactly this with on-device ML pose detection (e.g., ML Kit / MediaPipe) and keep every pixel on-device — turning "scans your silhouette, not your image" into an architectural guarantee instead of a marketing claim.
2. **Treat capture failures as first-class UX**: MeThreeSixty's worst reviews are about opaque failures ("refused to start", "says there's clutter"). Each guardrail (lighting, clutter, distance, full-body visibility) should name itself and its fix in the UI, and a failed capture should never lose the user's progress. Log on-device capture-quality metrics so users can see *why* a scan was rejected.
3. **Silhouette pipeline without photos at rest**: derive a body-width profile/mesh from the photos, keep the derived silhouette + measurements as the stored artifact, and never persist the raw image unless the user opts in (progress photos can live in an encrypted local store the user controls). Communicate "photos processed and discarded on-device" as a verifiable claim — this beats MeThreeSixty's ambiguous posture.
4. **Never paywall a user's own history**: "pay to see your past scans" is the most-damning complaint pattern in the reviews. In WLO, all locally-stored history, scans, and comparisons must be free forever; monetize AI conveniences (optional remote keys) instead. Also avoid forced-update kill switches for a local-first app — old versions should keep reading local data.
5. **Manual override on every estimate**: reviewers explicitly want to correct scan-derived numbers with tape-measure reality. WLO should let users edit/override any estimated measurement (and show estimate vs manual as separate series), which converts accuracy skepticism into trust and gives the numbers-geek two comparable series.
6. **Comparison mechanics worth copying**: a user-selectable "reference scan" baseline, side-by-side avatar/silhouette diff, and a FutureMe-style goal-body slider. If WLO does a projection slider, interpolate the silhouette conservatively (MeThreeSixty's "wrinkly skin / changing limb lengths" artifacts show the failure mode of naive morphing).
7. **The scale lies — visualize the discrepancy**: repeated 5-star reviews prove "losing size instead of weight" is emotionally decisive. WLO should always pair weight trend (moving average, as MeThreeSixty v4 does) with circumference/silhouette change so a stalled scale never looks like a stalled journey.
8. **Model honesty for demographics**: complaints from women 50+ about muscle/fat misattribution show estimation models must expose confidence ranges and be honest about what they can't know (WLO: show uncertainty bands on body-fat estimates rather than false precision).
9. **Keep the SDK surface clean**: a "privacy-first" app with a Facebook SDK is a one-review uninstall. A local-first WLO should have zero third-party trackers — and can prove it (e.g., observable network traffic claims in-app).

## Sources

- [MeThreeSixty official site](https://www.methreesixty.com/) (features, Premium list, press quotes, Size Stream company info)
- [MeThreeSixty FAQs](https://www.methreesixty.com/faqs) (capture requirements, privacy/deletion, accuracy, FutureMe, scan troubleshooting)
- [Our Health Metrics](https://www.methreesixty.com/our-health-metrics) (metric definitions incl. Fitness Index, visceral fat, WHR)
- [MeThreeSixty Privacy Policy](https://www.methreesixty.com/privacypolicy) (Body Scan Data, Dosing Information, CCPA/WA MHMDA notices)
- [What's New / version history](https://www.methreesixty.com/whats-new) (v1.0 2020 → v5.x 2026 evolution: scan suit → two photos → AI pose guidance → Premium)
- [Support page](https://www.methreesixty.com/support-help) (~1 million users claim; Measurement Guide)
- [Body Fat Calculator](https://www.methreesixty.com/body-fat-calculator) (published Size Stream Body Fat formulas)
- [App Store listing: MeThreeSixty: 3D Body Scan・BMI](https://apps.apple.com/us/app/methreesixty-3d-body-scan-bmi/id1472541261) (description, 4.75★/18.4k ratings, version 5.3.1, Aug 2026)
- [App Store user reviews RSS (id1472541261)](https://itunes.apple.com/us/rss/customerreviews/id=1472541261/sortBy=mostRecent/json) (all quoted complaints and praise)
- [Size Stream](https://www.sizestream.com/) (parent company, B2B scanning tech)
- Press quotes collected on the official site: [Apps400](https://apps400.com/iphone-apps/methreesixty-the-future-of-3d-body-scanning-and-weight-tracking.html), [WebAppRater](https://webapprater.com/reviews/ios-app/methreesixty-see-your-bodys-progress-like-never-before.html), [TapScape](https://www.tapscape.com/methreesixty/), [iPhoneGlance](https://www.iphoneglance.com/2025/02/01/app-review-methreesixty-a-revolutionary-3d-body-scanner-for-your-iphone/), [FanAppic](https://fanappic.com/methreesixty-3d-body-scanner-review/)
