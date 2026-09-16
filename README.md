# WLO

WLO is a local-first Android weight-management companion for people who want
clear numbers without accounts, ads, subscriptions, or shame-based copy.
User data stays on the device. Derived values expose their provenance, and
weak data is held instead of guessed.

The application is under active development. The current product focus is the
weight loop: weight-first onboarding, fast weigh-ins, trend and goal context,
direction-correct forecasting, Health Connect ingestion, and portable backups.

## Start here

- [Product objective](docs/objective.md)
- [Feature map and frozen rulings](docs/features/FEATURES.md)
- [Architecture](docs/tech/ARCHITECTURE.md)
- [Design system](docs/design/DESIGN-SYSTEM.md)
- [Dogfood distribution](docs/tech/DISTRIBUTION.md)

## Build

WLO requires JDK 21 and the Android SDK:

```sh
./gradlew :app:assembleDebug
```

Run the complete local verification gate with:

```sh
./gradlew build checkArchitecture
./gradlew -p build-logic test
```

Signed APKs are published only after the complete GitHub Actions gate passes.
The rolling `alpha` release follows `main`; stable releases use `vX.Y.Z` tags.
