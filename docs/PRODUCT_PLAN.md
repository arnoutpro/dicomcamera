# DICOM Camera (Android) — Product Plan

Vendor-independent Android app for clinical photo/video documentation: capture at the point of care, bind to the correct patient/order via DICOM Worklist or PACS query, store to PACS, then purge local copies.

**Primary market:** Europe, with the **Netherlands first** (AVG, NEN 7510 expectations, Dutch hospital IT / EPD landscape). Other EU markets follow the same MDR + GDPR baseline.

## Positioning

Alternatives researched:

| | Raster DICOM Camera | Alphatron / JiveX PhotoApp | This product |
|---|---|---|---|
| Platforms | iOS-first (Android mentioned) | iOS + Android | **Android only (MVP)** |
| PACS | DICOM-compatible | PACS-independent; deep JiveX integration | **Strict vendor independence** (DIMSE + DICOMweb) |
| Storage | Offline queue + sync | Encrypted; not in device gallery; send to PACS | **No durable local archive; ephemeral until C-STORE/STOW success, then wipe** |
| Workflows | Query / capture / upload-from-photos | Planned (EPD order) + unplanned | **MWL + PACS query to append to existing exam** |
| Media | Images (+ processing/annotation) | Medical photos | **Photos and videos** |

Differentiators for MVP:

1. Android-first, hospital MDM deployable
2. True PACS/EHR vendor independence (standard DICOM/HL7/IHE only)
3. Append-to-existing-study as a first-class workflow
4. Zero residual PHI on device after successful send

---

## MVP scope (must ship)

- [ ] Connect to PACS for **Query/Retrieve (QR)** and **Store**
- [ ] **DICOM Modality Worklist** (MWL)
- [ ] Capture **photos and videos**
- [ ] **EHR- and PACS-vendor independent**
- [ ] **No local storage after successful send to PACS**
- [ ] **Query PACS and add additional photos/videos to a current order/exam**

Out of MVP (explicitly later): iOS, annotation/markup suite, offline long-lived queues, deep proprietary EHR UI plugins, advanced image processing.

---

## Standards & compliance map

### DICOM (PS3)

| Capability | Service / SOP | Role |
|---|---|---|
| Connectivity check | Verification (C-ECHO) | SCU |
| Worklist | Basic Worklist Management (C-FIND) | SCU |
| Study/patient query | Query/Retrieve – FIND (Study Root) | SCU |
| Optional retrieve | C-MOVE / C-GET or WADO-RS | SCU (metadata/context only in MVP) |
| Store images/video | Storage (C-STORE) and/or STOW-RS | SCU |
| Encoding | VL Photographic Image, Secondary Capture, Multi-frame / Encapsulated Video as applicable | Creator |

Key identity tags to preserve when appending to an exam: Patient ID, Study Instance UID, Accession Number, Requested Procedure ID, Scheduled Procedure Step ID; generate new Series Instance UID + SOP Instance UIDs for new captures.

### HL7 v2 vs FHIR — what we actually need

**Imaging path = DICOM. Not FHIR.**  
Pixels, study UIDs, worklist, and PACS store stay on DICOM (DIMSE and/or DICOMweb). That is the MVP integration surface and what makes us PACS-vendor independent.

**HL7 v2** already feeds most hospital worklists: ADT/ORM (or equivalent) land in the RIS/broker, which exposes **Modality Worklist**. The app consumes MWL as a DICOM SCU. We do **not** implement HL7 v2 in the Android client for MVP.

**FHIR is optional EHR “front door” only — not part of MVP.**  
Some modern EPDs can launch or deep-link a context (`Patient`, `ServiceRequest`, `ImagingStudy`) via FHIR / SMART-on-FHIR. That can help open the right patient/order on the phone when MWL is awkward to expose to mobile devices. It does **not** replace C-STORE/STOW or MWL for sites that already have them.

| Concern | MVP approach | Later (Phase 5+, only if a pilot needs it) |
|---|---|---|
| Who is the patient / which order? | MWL + PACS C-FIND + barcode/Accession | Optional FHIR read / SMART launch |
| Where do images go? | DICOM C-STORE / STOW-RS → PACS | Still DICOM (FHIR DocumentReference is not our primary archive path) |
| Dutch EPD variety (HiX, Epic, etc.) | Stay standards-based; avoid vendor SDKs | Per-site FHIR profiles (e.g. Nictiz-oriented) only when required |

Keep demographics source-of-truth at RIS/EPD; the app is a **modality**, not an MPI.

### IHE (target actors)

| Profile | Actor intent |
|---|---|
| Radiology **Scheduled Workflow (SWF)** | Acquisition Modality (MWL → acquire → store) |
| **Consistent Presentation / patient ID** hygiene | Correct demographics from worklist, not free-text when avoidable |
| Radiology **Web-based Image Capture (WIC)** | Optional path: mobile capturer → Image Manager via DICOMweb |
| ITI **ATNA** | Audit trail of query/store/auth events |
| ITI **CT** (Consistent Time) | Device clock sync expectation (NTP via MDM) |

### Security / privacy / regulatory posture (EU / NL first)

Treat as handling **bijzondere persoonsgegevens / health data** from day one. Target buyers: Dutch (then EU) hospitals and clinics.

**Privacy (AVG / GDPR)**

- Run a **DPIA (GEB)** early — before pilot with real patients; refresh when workflows change
- Lawful basis + processing agreement (**verwerkersovereenkomst**) with each zorginstelling; clarify controller vs processor roles (typically: zorginstelling = verwerkingsverantwoordelijke, we = verwerker for any telemetry; on-device processing under their instruction)
- Data minimization: retention on device = **until successful PACS ACK**, then wipe; no gallery; no backup to Google Photos
- Logging: prefer technical/audit metadata over clinical content; define retention of audit logs
- Align with Dutch hospital expectations around **NEN 7510** (and related 7512/7513 for exchange/logging) in security design and supplier questionnaires
- Rights of data subjects handled via the zorginstelling’s process; app supports purge and access constraints

**Medical device (EU MDR)**

- Intended purpose draft in Phase 0: clinical photographic/video documentation for inclusion in the patient imaging record via PACS — **not** autonomous diagnosis
- Classification analysis with regulatory counsel (documentation aid vs Rule 11 software device is a real decision; do not assume “not a device”)
- If in scope of MDR: quality management (e.g. ISO 13485-aligned), clinical evaluation, technical file, UDI, post-market surveillance — architecture must support controlled releases, SBOM, traceability from day one
- Dutch market: same MDR baseline; local procurement often asks for NEN 7510 conformity evidence + DPIA outcomes

**Security baseline**

- Encrypted app-private storage only; never write to system gallery / MediaStore
- TLS for DICOMweb; DICOM TLS (or VPN-only hospital LAN) for DIMSE
- Authn: device + user (hospital IdP later); configurable AE Titles
- Wipe policy: delete local objects immediately after PACS success ACK; crash-safe purge on next launch
- Hospital IT: MDM config (AE Title, PACS host, TLS certs, feature flags); no consumer Play-store-only assumption for production

---

## Architecture (proposed)

```
┌─────────────────────────────────────────────────────────┐
│  Android app (Kotlin)                                   │
│  UI → Capture → DICOM encoder → Network → Purge         │
│         ↑              ↑                                │
│    CameraX        DICOM toolkit                         │
└─────────┬──────────────┬────────────────────────────────┘
          │              │
   DIMSE (C-ECHO/FIND/STORE)     DICOMweb (QIDO/STOW/WADO)
          │              │
          └──────┬───────┘
                 ▼
        Any standards-compliant PACS / VNA
                 ▲
        MWL SCP (often RIS or broker)
```

**Stack leanings (to validate in Phase 0):**

- UI: Kotlin + Jetpack Compose + CameraX
- DICOM: native DCMTK via NDK **or** pure-JVM dcm4che — pick one primary stack in Phase 0 spike
- Local test harness: Orthanc and/or dcm4chee + sample MWL
- Config: encrypted SharedPreferences / DataStore; MDM managed configurations

**Non-goals for architecture:** cloud intermediary that re-stores images (keeps PHI out of our servers unless a site explicitly deploys an optional broker).

---

## Phased delivery

### Phase 0 — Foundations (no clinical workflow yet)

**Goal:** Runnable Android skeleton, local PACS lab, standards decisions locked.

- Repo layout, CI, signing, Build flavors (dev/staging)
- Spin up Orthanc (C-STORE/C-FIND) + MWL test SCP
- Spike: C-ECHO + encapsulate one JPEG as DICOM SC + C-STORE
- Spike: CameraX photo → temp encrypted file → delete API
- Decide DIMSE library and whether DICOMweb is Phase 2 or Phase 3
- Threat model + data-flow diagram (capture → encode → store → wipe)
- Compliance checklist stub (DICOM conformance statement outline)
- Draft **intended purpose** (MDR) + DPIA/GEB outline for NL pilots; flag open classification questions for counsel

**Exit criteria:** Hello-PACS demo on a device/emulator; written ADR for DICOM stack; DPIA outline + intended-purpose draft exists.

---

### Phase 1 — Store path MVP (manual patient)

**Goal:** Safest useful loop: identify patient manually → photo → DICOM → PACS → wipe.

- PACS node settings UI (host, port, called/calling AE Title, TLS toggle)
- C-ECHO connectivity test
- Manual demographics form (Patient ID, Name, DOB, Sex, Accession optional)
- Photo capture (CameraX), preview, retake
- Encode VL Photographic Image or Secondary Capture with required Type 1/2 tags
- C-STORE with progress + success/failure UX
- **Mandatory wipe** of pixel data + DICOM file after success
- Failure path: keep **only** encrypted ephemeral queue until retry succeeds or user discards (no gallery leakage)

**Exit criteria:** Images appear correctly in Orthanc/PACS viewer with demographics; device storage has zero residual study files after success.

---

### Phase 2 — Worklist + append-to-exam

**Goal:** Real clinical identity binding and “add more photos to this exam.”

- MWL C-FIND SCU (filters: date, modality, station AE, Patient ID, Accession)
- Worklist picker → populate all study/order tags; create Series per capture session
- Study-level C-FIND (QR) by Patient ID / Accession / Study Instance UID
- **Append workflow:** select existing study → new Series → Store with same Study Instance UID
- Guardrails: confirm patient banner before every shutter press; prevent cross-patient append
- Basic audit log entries (who/what/when/study UID)

**Exit criteria:** Scheduled case from MWL and append-to-existing-study both verified against test PACS; Conformance Statement draft covers MWL + Storage + Query.

---

### Phase 3 — Video + session UX

**Goal:** Photos and videos in one exam session; reliable send; still no durable local archive.

- Video capture via CameraX; encode to agreed DICOM video SOP (or multi-frame policy documented)
- Multi-shot session tray (in-memory / encrypted staging only)
- Batch store with per-instance status; wipe each instance on ACK
- Network resilience: retry with backoff; discard/export policy if PACS unreachable (prefer block capture when offline for MVP strictness, or short encrypted staging — product decision)
- Series descriptions / body part / laterality codes (CID where practical)

**Exit criteria:** Mixed photo+video study stored and queryable; purge verified after batch success.

---

### Phase 4 — Vendor independence hardening + IHE alignment

**Goal:** Works across PACS brands; hospital-ready integration story.

- Dual stack: **DIMSE** and **DICOMweb** (QIDO-RS query, STOW-RS store) selectable per site
- Character set / timezone / date handling edge cases
- DICOM TLS + private CA install via MDM
- Android Managed Configurations (no manual AE typing for end users)
- IHE SWF modality actor checklist; optional WIC path documented
- ATNA-style audit export (syslog/TLS or file for SIEM)
- Draft **DICOM Conformance Statement** + deployment guide for IT

**Exit criteria:** Verified against ≥2 PACS products (e.g. Orthanc + one commercial/VNA); IT deploy doc complete.

---

### Phase 5 — EHR launch options + NL/EU compliance packaging

**Goal:** Pilot-ready for a Dutch/EU hospital IT, security, and privacy review — still vendor-independent.

- Context launch without FHIR first: QR/barcode of Accession or Patient ID (works with any EPD sticker/wristband workflow)
- **FHIR only if a concrete pilot requires it** (see HL7/FHIR section): read `Patient` / `ServiceRequest` / `ImagingStudy` or SMART launch — never as the image archive API
- Unscheduled / emergency workflow (create study with generated UIDs under local policy)
- Role-based access (operator vs admin config)
- Privacy UX: clear “niet blijvend op dit apparaat” messaging; MDM remote wipe assumptions
- Compliance pack: DPIA/GEB, verwerkersovereenkomst template, NEN 7510 questionnaire answers, SBOM, versioned releases, test evidence
- MDR pathway decision recorded with counsel; QMS artifacts if classified as device

**Exit criteria:** Pilot-ready build + NL-oriented privacy/security pack; FHIR deferred unless a named site blocks without it.

---

## Suggested module map (implementation)

```
app/
  ui/          # Compose screens: worklist, capture, review, settings
  capture/     # CameraX photo/video
  dicom/       # Encode SOP instances, UID generation
  network/     # DIMSE + DICOMweb clients
  identity/    # MWL + QR query models
  security/    # crypto, wipe, secure staging
  audit/       # local audit trail
  config/      # PACS nodes, MDM
```

---

## Testing strategy

| Layer | What |
|---|---|
| Unit | Tag builders, UID rules, wipe guarantees, query filters |
| Integration | Orthanc + MWL SCP in CI/docker |
| Device | CameraX on real Android hardware; MDM config smoke |
| Conformance | Store/MWL/Find against validator / dciodvfy where applicable |
| Security | No MediaStore leakage; leftover file scan after kill/crash |

---

## Open decisions (resolve in Phase 0–1)

1. **DICOM toolkit:** DCMTK (NDK) vs dcm4che (JVM) vs hybrid
2. **Offline policy:** block capture when PACS unreachable vs short encrypted staging queue
3. **Video SOP class:** which encapsulated/multi-frame profile to standardize on
4. **Primary transfer syntax:** JPEG Baseline vs JPEG-LS vs uncompressed for photos
5. **MDR classification** for stated intended purpose (NL/EU counsel) — drives QMS depth
6. **Auth model:** AE-only LAN trust vs user login (OIDC/SAML) in MVP
7. **FHIR:** confirm stay deferred until a Dutch pilot EPD explicitly needs SMART/FHIR launch (default: yes, defer)

---

## Phase priority for build start

Ship value in this order: **Phase 0 → 1 → 2 → 3**, with Phase 4 work overlapping Phase 2–3 (DICOMweb spike early). DPIA/MDR drafting starts in Phase 0 and hardens through Phase 5. FHIR stays off the critical path unless a pilot forces it.

When Phase 0 starts, first concrete tasks:

1. Android project scaffold (Compose + CameraX)
2. Docker Orthanc (+ MWL) for local/CI
3. DICOM stack spike: Echo + Store one photo
4. Secure staging + wipe proof test
