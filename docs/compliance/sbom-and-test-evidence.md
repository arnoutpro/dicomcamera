# SBOM and test evidence (release process)

## Versioning

- `versionName` encodes phase (e.g. `0.6.0-phase5`)
- `versionCode` increments per storeable build
- Flavors: `dev` (emulator Orthanc defaults), `staging` (empty clinical defaults)

## SBOM

Generate at release from Gradle dependencies, e.g.:

```bash
./gradlew :app:dependencies --configuration stagingDebugRuntimeClasspath > dist/sbom-deps.txt
```

Prefer CycloneDX / SPDX once CI is wired; until then attach the deps dump + APK hash to the release record.

## Test evidence (minimum)

| Layer | Command / artifact |
|---|---|
| Unit (DICOM) | `./gradlew :dicom:testDebugUnitTest` |
| Unit (identity) | `./gradlew :identity:testDebugUnitTest` |
| APK | `./gradlew :app:assembleStagingDebug` → `dist/dicomcamera-staging-debug.apk` |
| Device smoke | Capture → review → archive/pending on target MDM build |

Record pass/fail, git SHA, and device model in the pilot evidence folder.
