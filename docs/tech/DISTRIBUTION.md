# Dogfood distribution

WLO uses one Android application ID and one signing identity for both release
channels. This preserves on-device data when moving between alpha and stable
builds. Never install a debug-signed `app.wlo` over a dogfood installation:
Android cannot replace it with the channel certificate without uninstalling
the app, and uninstalling removes local data.

## Channels

| Channel | Source | GitHub release | Android version name |
|---|---|---|---|
| Alpha | Latest green `main` | Rolling prerelease tag `alpha` | `0.1.0-alpha.<run>+<sha>` |
| Stable | Green `vX.Y.Z` tag on `main` | Immutable release tag | `X.Y.Z` |

Both channels derive a monotonic `versionCode` from the full Git commit count.
The CI workflow runs the build, architecture checks, build-logic self-tests,
and API-29 instrumented suite before the publish job becomes eligible.

## Signing identity

The canonical channel keystore is kept outside the repository. GitHub Actions
requires all four repository secrets:

- `WLO_SIGNING_KEYSTORE_BASE64`
- `WLO_SIGNING_STORE_PASSWORD`
- `WLO_SIGNING_KEY_ALIAS`
- `WLO_SIGNING_KEY_PASSWORD`

CI pins the expected certificate SHA-256 digest and rejects a signed APK from
any other identity. Keep an encrypted, independently tested backup of the
keystore and credentials. Losing them means existing installations can never
be upgraded in place.

For a local signed build, copy `keystore.properties.example` to the ignored
`keystore.properties` file and point it at the canonical keystore. Without
that file or the equivalent environment variables, Gradle emits an unsigned
release APK; it never falls back to the debug certificate.

## Alpha installation

The low-infrastructure dogfood path is Obtainium against the repository's
GitHub Releases page. Configure it to accept prereleases and install the
`wlo-…apk` asset from the rolling `alpha` release. Review the generated release
notes before updating. Back up WLO data before testing migrations or other
high-risk changes.

## Cutting a stable release

1. Confirm `main` is green and the intended commit has completed dogfooding.
2. Create an annotated semantic-version tag, for example `v0.1.0`.
3. Push the tag. CI rejects malformed tags and tags not contained in `main`.
4. Verify the published APK certificate and install/update path on a real
   device before announcing the release.

The tag does not bypass CI. A stable release is published only after the same
verification gate as alpha.
