# DICOM Conformance Statement

**Application Entity Title (default):** `DICOMCAM` (configurable; MDM-managed)  
**Product:** Arnout.pro DICOM Camera (Android)  
**Document version:** 1.0  
**Software versions covered:** `0.6.x` (staging/dev builds; Implementation Version Name in File Meta may still read `DICOMCAM_0_5` until a coordinated encoder bump)  
**Implementation Class UID:** `2.25.33300112233445566778899`  
**Implementation Version Name:** `DICOMCAM_0_5`  
**Date:** 2026-08-12  

**Status:** Technical Conformance Statement **v1.0** — describes the shipping networking and encoding behaviour of this application as implemented. It is suitable for hospital IT / PACS integration review.

**Not included in this document:** CE marking, MDR classification decision, or a countersigned legal release. Those are separate from DICOM PS3.2 conformance. Before wide production deployment, replace the temporary `2.25.*` Implementation Class UID with an organisational root and record multi-vendor PACS evidence in the pilot folder.

---

## 0. Introduction

### 0.1 Revision history

| Version | Date | Notes |
|---|---|---|
| 0.x drafts | 2025–2026 | Phase 0–4 outline / Phase 4 summary |
| **1.0** | 2026-08-12 | Full technical statement aligned to dual-stack DIMSE + DICOMweb implementation |

### 0.2 Audience, remarks, terms

This statement follows the intent of DICOM PS3.2 (Conformance). It defines how **Arnout.pro DICOM Camera** behaves as a **Service Class User (SCU)** toward a PACS / VNA / MWL SCP. The product does **not** act as an SCP for Storage, Query/Retrieve, or Worklist.

**Intended use (summary):** capture clinical photographic and video documentation on a managed Android device, associate it with patient/order context, encode DICOM objects, and transmit them to a hospital archive. Not intended for autonomous diagnosis.

References: `docs/adr/0001-dicom-toolkit-dcm4che.md`, `docs/adr/0002-dicom-video-encoding.md`, `docs/adr/0003-dual-stack-dicomweb.md`, `docs/ihe/swf-modality-checklist.md`, `docs/deploy/IT_DEPLOYMENT_GUIDE.md`.

### 0.3 Implementation model

```
┌─────────────────────────────────────────────────────────────┐
│  Arnout.pro DICOM Camera (Android AE: DICOMCAM)             │
│  Identity (MWL / Manual / FHIR / HL7) → CameraX capture     │
│  → Encode (VL Photo / Video Photo) → Store → Wipe           │
└───────────────┬─────────────────────────────┬───────────────┘
                │ DIMSE                       │ DICOMweb
                │ C-ECHO / MWL / C-FIND /     │ HTTPS ping /
                │ C-STORE                     │ QIDO-RS / STOW-RS
                ▼                             ▼
         PACS / VNA / MWL SCP          DICOMweb Image Manager
```

One logical Application Entity. Transport mode is site-configurable (`DIMSE` or `DICOMWEB`). When transport is DICOMweb, **Modality Worklist** still uses DIMSE if host/AE are configured (IHE SWF).

---

## 1. Networking overview

### 1.1 Application Data Flow

1. Resolve patient/order (MWL C-FIND, Study Root C-FIND / QIDO-RS, Manual, or EHR FHIR/HL7 — EHR is outside DICOM).  
2. Capture photo (JPEG) and/or video (MP4/H.264).  
3. Encode DICOM File Meta + dataset; generate Study/Series/SOP Instance UIDs (`2.25.*` UUID OID arc) unless appending to an existing Study Instance UID.  
4. Store via C-STORE or STOW-RS; on success, wipe local pixels; on failure, keep an encrypted ephemeral pending queue (manual retry).  

### 1.2 Functional definitions of AEs

| AE | Role |
|---|---|
| DICOM Camera AE | Single SCU AE; Verification, Storage, MWL FIND, Study Root FIND; DICOMweb QIDO/STOW |

### 1.3 Sequencing

Typical: Verification (optional) → MWL or Study FIND → Store one or more SOP Instances in a session → association release. Associations are short-lived (connect per operation).

---

## 2. AE specifications

### 2.1 DICOM Camera AE — SOP classes

| SOP Class Name | SOP Class UID | Role | DIMSE | DICOMweb |
|---|---|---|---|---|
| Verification | `1.2.840.10008.1.1` | SCU | C-ECHO | HTTP reachability ping (non-DICOM) |
| VL Photographic Image Storage | `1.2.840.10008.5.1.4.1.1.77.1.4` | SCU | C-STORE | STOW-RS |
| Video Photographic Image Storage | `1.2.840.10008.5.1.4.1.1.77.1.4.1` | SCU | C-STORE | STOW-RS |
| Secondary Capture Image Storage | `1.2.840.10008.5.1.4.1.1.7` | SCU | C-STORE (legacy encoder retained; new captures use VL Photographic) | — |
| Modality Worklist Information Model – FIND | `1.2.840.10008.5.1.4.31` | SCU | C-FIND | Not over DICOMweb (DIMSE fallback) |
| Study Root Query/Retrieve Information Model – FIND | `1.2.840.10008.5.1.4.1.2.2.1` | SCU | C-FIND (STUDY) | QIDO-RS studies |

**Not supported:** Storage Commitment, MPPS, C-MOVE / C-GET retrieve of pixels, Media Storage (DICOMDIR), Print, UPS-RS, WADO-RS pixel retrieve.

### 2.2 Association policies

| Policy | Behaviour |
|---|---|
| General | Initiates associations as SCU; does not accept incoming associations |
| Number of associations | One concurrent association per operation (echo / store / find) |
| Asynchronous | Not used (no async operations window) |
| Implementation Class UID | `2.25.33300112233445566778899` |
| Implementation Version Name | `DICOMCAM_0_5` |
| Called / Calling AE Titles | Configurable; default calling `DICOMCAM` |
| TLS | Optional for DIMSE (`pacs_use_tls`); uses Android system trust store (hospital CA via MDM). DICOMweb uses HTTPS when the configured URL is `https://` |

### 2.3 Association initiation — presentation contexts

#### Verification

| Abstract Syntax | Transfer Syntaxes proposed |
|---|---|
| Verification | Implicit VR Little Endian |

#### Storage

| Abstract Syntax | Transfer Syntaxes proposed (preference order) |
|---|---|
| VL Photographic Image Storage | JPEG Baseline 8-bit, Explicit VR LE, Implicit VR LE |
| Secondary Capture Image Storage | JPEG Baseline 8-bit, Explicit VR LE, Implicit VR LE |
| Video Photographic Image Storage | MPEG-4 AVC/H.264 HP Level 4.1 (`1.2.840.10008.1.2.4.102`), Explicit VR LE, Implicit VR LE |

Created instances are written with:

| SOP | Transfer Syntax UID | Notes |
|---|---|---|
| VL Photographic | `1.2.840.10008.1.2.4.50` (JPEG Baseline) | Photometric `YBR_FULL_422`; encapsulated JPEG |
| Video Photographic | `1.2.840.10008.1.2.4.102` (MPEG4HP41) | Photometric `YBR_PARTIAL_420`; encapsulated MP4/H.264 bitstream (CameraX) |

#### Modality Worklist FIND

| Abstract Syntax | Transfer Syntaxes |
|---|---|
| Modality Worklist Information Model – FIND | Explicit VR LE, Implicit VR LE |

**Matching keys commonly sent:** Patient ID, Patient Name, Accession Number, Scheduled Procedure Step Start Date, Modality (default `XC`), Scheduled Station AE Title (calling AE).

#### Study Root FIND

| Abstract Syntax | Transfer Syntaxes |
|---|---|
| Study Root Query/Retrieve Information Model – FIND | Explicit VR LE, Implicit VR LE |

**Level:** STUDY. Keys: Patient ID, Patient Name, Accession Number, Study Instance UID (and return keys for demographics / description / modalities).

### 2.4 DICOMweb (when transport = DICOMWEB)

| Operation | Method | Path pattern |
|---|---|---|
| Reachability | HTTP(S) GET | Configured base URL |
| QIDO-RS studies | GET | `{base}/studies?…` |
| STOW-RS | POST multipart related | `{base}/studies` |

Bearer / OAuth / Basic auth for DICOMweb are **not** implemented in this version (open network or gateway-side auth only).

### 2.5 SOP-specific conformance — Storage SCU

- Modality tag typically `XC`.  
- Manufacturer `DICOM Camera`; Model name reflects build line (e.g. `Android Phase4`).  
- `Timezone Offset From UTC` (0008,0201) written.  
- Character set: `ISO_IR 100` by default; `ISO_IR 192` when demographics require UTF-8.  
- Empty Type 2 sequences where required (e.g. Acquisition Context Sequence).  
- Append workflow reuses Study Instance UID from MWL / Study FIND / QIDO; new Series and SOP Instance UIDs are generated.  
- Successful store → local pixel/DICOM staging wiped. Failure → pending queue (manual resend; TTL ~4 hours).

### 2.6 SOP-specific conformance — MWL / Query SCU

- MWL is DIMSE-only.  
- Study FIND supports append-to-existing-exam.  
- No C-MOVE/C-GET of prior images to the device.

---

## 3. Network interfaces

| Interface | Details |
|---|---|
| Physical | Device Wi-Fi / cellular as provided by the Android OS and hospital network |
| TCP/IP | IPv4 (as configured on device) |
| DICOM Upper Layer | dcm4che 5.31.x stack |
| Configuration | In-app Settings and/or Android Managed Configurations (host, port, AE Titles, TLS, DICOMweb URL, transport mode) |

---

## 4. Media interchange

**Not supported.** No DICOM File-set / DICOMDIR import/export for clinical exchange. Local files are ephemeral staging only.

---

## 5. Support of extended character sets

| Value | When |
|---|---|
| `ISO_IR 100` | Default Latin-1 |
| `ISO_IR 192` | Auto when patient/study text contains non-ASCII |

Person Names use DICOM PN (`FAMILY^GIVEN`). UI may display a humanised form; storage keeps PN encoding.

---

## 6. Security

| Topic | Support |
|---|---|
| DICOM TLS (DIMSE) | Optional; system trust store |
| HTTPS (DICOMweb) | Via `https://` base URL |
| User authentication over DICOM | AE Title trust on LAN; no DICOM User Identity Negotiation in this version |
| DICOMweb OAuth/Basic | Not implemented |
| PHI on device | App-private encrypted staging; no gallery/MediaStore; wipe after success |
| Audit | Local CSV; export ATNA-style syslog text for SIEM |

---

## 7. Annexes

### A — IHE alignment (informative)

| Profile intent | Product mapping |
|---|---|
| Radiology SWF – Acquisition Modality | MWL → acquire → store |
| WIC (Web-based Image Capture) | STOW-RS + QIDO path when DICOMweb selected |
| ATNA | File export of audit events (pull/MDM); not a live syslog sink |

### B — Configuration checklist for PACS admins

1. Register calling AE `DICOMCAM` (or site value) for C-ECHO, C-STORE, C-FIND, MWL as required.  
2. Accept Storage SOP Classes: VL Photographic + Video Photographic (+ SC if testing legacy).  
3. Accept transfer syntaxes JPEG Baseline and MPEG4HP41.  
4. For Orthanc worklists: enable Worklists plugin; allow FIND from unknown AEs or register the device AE.  
5. For DICOMweb: publish QIDO/STOW root; ensure STOW accepts encapsulated JPEG / MPEG-4 instances.

### C — Known limitations / validation status

| Item | Status |
|---|---|
| Lab validation | Orthanc (DIMSE + DICOMweb + MWL plugin) exercised in development |
| Second commercial / VNA | Required for Phase 4 exit criterion — record per pilot site |
| Implementation Class UID | Temporary `2.25.*` — replace with organisational OID before formal production branding |
| Storage Commitment / MPPS | Out of scope for current MVP |
| Formal “signed” release of this statement | Product/regulatory process; content above is the technical baseline |

### D — Related documents

- `docs/compliance/dicom-conformance-outline.md` — historical phase outline (superseded by this statement for claimed services)  
- `docs/compliance/DICOM_CONFORMANCE_STATEMENT_DRAFT.md` — previous short draft (replaced by this v1.0)  
- `docs/deploy/IT_DEPLOYMENT_GUIDE.md`  
- `docs/ihe/swf-modality-checklist.md`, `docs/ihe/wic-path.md`
