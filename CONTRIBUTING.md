# Contributing

Thanks for helping improve **Arnout.pro DICOM Camera**.

This repository is aimed at hospitals, PACS engineers, and Android developers who care about **standards-based** clinical photo/video capture on mobile. Please read [`DISCLAIMER.md`](DISCLAIMER.md) before testing: current builds are **lab / pilot preparation only — not for clinical use**.

## Ground rules

1. **No real patient data** in issues, PRs, commits, screenshots, or `lab/` fixtures. Synthetic IDs only.  
2. **No production credentials** (PACS passwords, bearer tokens, VPN configs, keystores).  
3. Prefer **DICOM / FHIR / HL7 / IHE** behaviour over vendor lock-in.  
4. Keep changes focused; match existing Kotlin / Compose style.  
5. Assume deployers need clear, honest docs — especially around privacy and lab-only status.

## Ways to contribute

| Kind | How |
|---|---|
| Bug report | GitHub Issue — steps, flavor (`dev`/`staging`), PACS type (e.g. Orthanc), no PHI |
| Integration question | Issue with AE Titles / ports redacted if sensitive |
| Code fix or feature | Fork → branch → Pull Request against `main` |
| Docs / DCS / deploy notes | PRs welcome; mark drafts clearly |
| Security issue | See [`SECURITY.md`](SECURITY.md) — do not open a public issue with exploit detail |

## Development setup

**Requirements:** JDK 17+, Android SDK, optional Docker for the lab.

```bash
./gradlew :identity:testDebugUnitTest :dicom:testDebugUnitTest :app:testDevDebugUnitTest
./gradlew :app:assembleStagingDebug
```

Local PACS (and, when merged, EHR harnesses):

```bash
cd lab
docker compose up -d
```

See root [`README.md`](README.md) and [`lab/README.md`](lab/README.md).

Sideload notes for the committed preview APK: [`dist/README.md`](dist/README.md).

## Pull requests

1. Branch from current `main` (`cursor/…` or `fix/…` — any clear name).  
2. Keep the PR scoped to one concern.  
3. Include a short summary: **what** changed and **why**.  
4. Run the unit tests above; note if you could not run device/lab tests.  
5. Update docs when behaviour or public APIs change (especially `docs/compliance/DICOM_CONFORMANCE_STATEMENT.md` for DICOM networking / SOP changes).  
6. Do not bump `dist/*.apk` unless the PR is intentionally refreshing the sideload drop (and bump `versionCode` / `versionName`).

### PR checklist

- [ ] No PHI / secrets in the diff  
- [ ] Tests added or updated when logic changes  
- [ ] Lab-only / disclaimer wording preserved where relevant  
- [ ] MDM / Settings keys documented if you add managed config  

## Code map

```
app/       Compose UI, CameraX, settings, MDM
dicom/     Encode, DIMSE, DICOMweb, audit, staging wipe
identity/  PatientDirectory — MWL, FHIR, HL7 façade
lab/       Orthanc (+ EHR mocks when present)
docs/      Plan, compliance, deploy, IHE
branding/  Icons and store graphics
```

## Design / product notes

- Privacy by design: app-private staging, wipe after successful PACS ACK, no system gallery.  
- Identity: prefer worklist / EHR lookup over hand-typed demographics.  
- In-app **lab-only** banner and Settings → About are intentional — do not remove them to “look production ready.”

## License

By contributing, you agree that your contributions are licensed under the same **Apache License 2.0** as the project (`LICENSE`).

## Contact

- Issues: [arnoutpro/dicomcamera](https://github.com/arnoutpro/dicomcamera)  
- Brand: [Arnout.pro](https://arnout.pro)
