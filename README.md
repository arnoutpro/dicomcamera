# dicomcamera

Android clinical camera: capture photos/videos as DICOM, bind via Modality Worklist or PACS/EHR query, store to any standards-compliant PACS, then purge local copies.

**Android only. PACS/EHR vendor independent. No durable on-device archive after successful send.**  
**Primary market:** Netherlands / EU (AVG, NEN 7510, MDR-aware).

## Status — Phase 2

- Manual patient, **Modality Worklist**, and **append to existing study**
- Capture → review → VL Photographic C-STORE → wipe
- Pending queue + local audit log

See **[docs/PRODUCT_PLAN.md](docs/PRODUCT_PLAN.md)**.

## Quick start

```bash
./gradlew :dicom:testDebugUnitTest
./gradlew :app:assembleStagingDebug
```
