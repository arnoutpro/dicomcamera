# dicomcamera

Android clinical camera: capture photos/videos as DICOM, bind via Modality Worklist or PACS/EHR query, store to any standards-compliant PACS, then purge local copies.

**Android only. PACS/EHR vendor independent. No durable on-device archive after successful send.**  
**Primary market:** Netherlands / EU (AVG, NEN 7510, MDR-aware).

## Status — Phase 0 in progress

Foundations landed on this branch:

- Android app (`dev` / `staging` flavors) with CameraX capture spike
- `:dicom` module — Secondary Capture JPEG encode, C-ECHO/C-STORE (dcm4che), secure staging wipe
- `:identity` module — `PatientDirectory` / `OrderDirectory` seams (manual now; MWL/HL7/FHIR later)
- In-process PACS SCP unit tests (no Docker required in CI)
- Orthanc docker-compose lab under `lab/`
- Docs: ADR, threat model, MDR intended purpose draft, DPIA outline, conformance outline

See **[docs/PRODUCT_PLAN.md](docs/PRODUCT_PLAN.md)** for the full phased plan.

## Quick start

```bash
./gradlew :dicom:testDebugUnitTest
./gradlew :app:assembleDevDebug
```

Optional Orthanc lab (Docker on your machine):

```bash
cd lab && docker compose up -d
```

Emulator → Orthanc defaults: host `10.0.2.2`, port `4242`, AE `ORTHANC`.
