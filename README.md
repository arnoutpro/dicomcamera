# dicomcamera

Android clinical camera: capture photos/videos as DICOM, bind via Modality Worklist or PACS/EHR query, store to any standards-compliant PACS, then purge local copies.

**Android only. PACS/EHR vendor independent. No durable on-device archive after successful send.**  
**Primary market:** Netherlands / EU (AVG, NEN 7510, MDR-aware).

## Status — Phase 1

Manual patient → capture → review/retake → VL Photographic C-STORE → wipe.  
Failed stores go to an on-device pending queue (retry/discard). PACS settings persist locally.

See **[docs/PRODUCT_PLAN.md](docs/PRODUCT_PLAN.md)** for the full phased plan.

## Quick start

```bash
./gradlew :dicom:testDebugUnitTest
./gradlew :app:assembleStagingDebug
```

Optional Orthanc lab:

```bash
cd lab && docker compose up -d
```

Configure PACS in-app (gear icon). Staging flavor starts with empty host defaults.
