# dicomcamera

Android clinical camera: capture photos/videos as DICOM, bind via Modality Worklist or PACS/EHR query, store to any standards-compliant PACS, then purge local copies.

**Android only. PACS/EHR vendor independent. No durable on-device archive after successful send.**  
**Primary market:** Netherlands / EU (AVG, NEN 7510, MDR-aware).

## Status — Phase 3

- Session tray: multi-shot photo + video in one exam
- VL Photographic + Video Photographic (MPEG-4 HP 4.1) C-STORE
- Batch send with retry/backoff; wipe on ACK; pending queue on failure
- Body part / laterality tags; worklist + append from Phase 2

See **[docs/PRODUCT_PLAN.md](docs/PRODUCT_PLAN.md)** and **[docs/adr/0002-dicom-video-encoding.md](docs/adr/0002-dicom-video-encoding.md)**.

## Quick start

```bash
./gradlew :dicom:testDebugUnitTest
./gradlew :app:assembleStagingDebug
```
