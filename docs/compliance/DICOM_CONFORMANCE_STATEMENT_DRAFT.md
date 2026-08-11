# DICOM Conformance Statement — Draft (Phase 4)

**Application:** DICOM Camera (Android)  
**Version:** 0.5.x  
**Implementation Class UID:** `2.25.33300112233445566778899`  
**Implementation Version Name:** `DICOMCAM_0_5`  
**Default AE Title:** `DICOMCAM` (configurable / MDM)

This is a **draft** for IT review. A formal signed statement will be issued before external production pilots.

## Networking roles

| SOP / service | Role | Transport |
|---|---|---|
| Verification | SCU | DIMSE |
| VL Photographic Image Storage | SCU | DIMSE C-STORE / DICOMweb STOW-RS |
| Video Photographic Image Storage | SCU | DIMSE C-STORE / DICOMweb STOW-RS |
| Secondary Capture Image Storage | SCU | DIMSE (legacy/lab) |
| Modality Worklist Information Model – FIND | SCU | DIMSE |
| Study Root Query/Retrieve Information Model – FIND | SCU | DIMSE / QIDO-RS (studies) |

## Transfer syntaxes

| Context | Syntax |
|---|---|
| VL Photographic | JPEG Baseline (Process 1) |
| Video Photographic | MPEG-4 AVC/H.264 HP Level 4.1 (`MPEG4HP41`) |
| Query/Verify | Explicit VR LE, Implicit VR LE |

## Character sets

- Default `ISO_IR 100`
- Automatic `ISO_IR 192` (UTF-8) when demographics contain non-ASCII

## Date / time

- Content / acquisition date-time use the device default time zone
- `Timezone Offset From UTC` (0008,0201) is written on created instances

## Security

- Optional DICOM TLS for DIMSE (system trust store; hospital CA via MDM)
- HTTPS recommended for DICOMweb
- No MediaStore / gallery persistence of clinical pixels
- Wipe after successful store; encrypted ephemeral pending queue on failure

## Audit

- Local append-only CSV audit
- Export to ATNA-style syslog text for SIEM hand-off

## Limitations

- MPPS / Storage Commitment not supported
- DICOMweb authentication (OAuth/Basic) not yet implemented
- MWL not available over DICOMweb (DIMSE fallback)

See also: `docs/ihe/swf-modality-checklist.md`, `docs/deploy/IT_DEPLOYMENT_GUIDE.md`, ADRs 0001–0003.
