# dicomcamera

Android clinical camera: capture photos/videos as DICOM, bind via Modality Worklist or PACS/EHR query, store to any standards-compliant PACS, then purge local copies.

**Android only. PACS/EHR vendor independent. No durable on-device archive after successful send.**  
**Primary market:** Netherlands / EU (AVG, NEN 7510, MDR-aware).

## Status — Phase 4

- Dual stack: **DIMSE** and **DICOMweb** (QIDO-RS / STOW-RS), selectable per site / MDM
- Session tray photo + video; batch store with retry; wipe on ACK
- Managed Configurations, ATNA-style audit export, charset/timezone hardening
- Draft Conformance Statement + IT deployment guide + IHE SWF/WIC notes

See **[docs/PRODUCT_PLAN.md](docs/PRODUCT_PLAN.md)**, **[docs/deploy/IT_DEPLOYMENT_GUIDE.md](docs/deploy/IT_DEPLOYMENT_GUIDE.md)**, ADR [0003](docs/adr/0003-dual-stack-dicomweb.md).

## Quick start

```bash
./gradlew :dicom:testDebugUnitTest
./gradlew :app:assembleStagingDebug
```
