# DICOM Conformance Statement — outline

Formal draft: [`DICOM_CONFORMANCE_STATEMENT_DRAFT.md`](DICOM_CONFORMANCE_STATEMENT_DRAFT.md).

## Implementation

| Item | Value |
|---|---|
| Application | DICOM Camera (Android) |
| Version | 0.5.x (Phase 4) |
| AE Title | Configurable / MDM (default `DICOMCAM`) |

## Networking

| Service | Role | Phase |
|---|---|---|
| Verification SOP Class | SCU | 0 |
| Secondary Capture Image Storage | SCU (Storage) | 0 |
| VL Photographic Image Storage | SCU (Storage) | 1 |
| Basic Worklist Management (C-FIND) | SCU | 2 |
| Study Root Query/Retrieve Information Model – FIND | SCU | 2 |
| Video Photographic Image Storage | SCU (Storage) | 3 |
| DICOMweb QIDO-RS / STOW-RS | SCU | 4 |

## Transfer syntaxes

- Explicit VR Little Endian
- Implicit VR Little Endian
- JPEG Baseline (Process 1) for VL Photographic
- MPEG-4 AVC/H.264 High Profile / Level 4.1 (`MPEG4HP41`) for Video Photographic

## UID generation

Temporary UUID OID arc `2.25.*` — replace with organizational root before production.
