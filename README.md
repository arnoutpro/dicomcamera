# dicomcamera

Android clinical camera: capture photos/videos as DICOM, bind via Modality Worklist or PACS/EHR query, store to any standards-compliant PACS, then purge local copies.

**Android only. PACS/EHR vendor independent. No durable on-device archive after successful send.**  
**Primary market:** Netherlands / EU (AVG, NEN 7510, MDR-aware).

## Status — Phase 5

- EHR identity: **FHIR R4 Patient** + **HL7 façade**, composite lookup modes
- Barcode/Patient ID trigger, emergency / no-order path, privacy banner
- Operator config lock + MDM keys for FHIR/HL7
- NL/EU compliance pack drafts + optional HL7 HTTPS connector note
- Phase 4 dual-stack PACS / ATNA retained

See **[docs/PRODUCT_PLAN.md](docs/PRODUCT_PLAN.md)** and **[docs/compliance/PHASE5_COMPLIANCE_PACK.md](docs/compliance/PHASE5_COMPLIANCE_PACK.md)**.

## Quick start

```bash
./gradlew :identity:testDebugUnitTest :dicom:testDebugUnitTest
./gradlew :app:assembleStagingDebug
```
