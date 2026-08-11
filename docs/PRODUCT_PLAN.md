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

Out of first clinical MVP build: iOS, annotation/markup suite, offline long-lived queues, deep proprietary EHR UI plugins, advanced image processing.

**Future-ready (design now, implement in identity phases):** HL7 v2 and FHIR adapters so the app can resolve patient/order details from the EPD on the fly — without locking to one EHR vendor.

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

### HL7 & FHIR — EHR identity on the fly (future-ready)

**Split of responsibilities**

| Path | Standard | Job |
|---|---|---|
| **Imaging** | DICOM (DIMSE / DICOMweb) | Worklist (when available), query existing exams, store photos/videos, append to study |
| **Identity / demographics** | **HL7 v2** and **FHIR** | Look up patient (and optionally order) details from the EPD/EHR on the fly |

Pixels never go through FHIR/HL7 as the archive API. Demographics should not be typed by hand when the EHR can answer.

**Why both HL7 v2 and FHIR**

- **HL7 v2** is still what most Dutch/EU hospitals run today (ADT, QBP/RSP or similar query patterns, ORM/ORU via the interface engine). Handy for *“scan/enter Patient ID → fetch name, DOB, sex, … now”* without waiting for a radiology worklist entry.
- **FHIR** is the forward path (and increasingly what EPDs expose): `Patient`, `ServiceRequest`, `ImagingStudy`, SMART-on-FHIR launch/context. Same product capability, modern transport and authorization.
- Keeping **both** behind one internal `PatientDirectory` / `OrderDirectory` interface = vendor independence + future-proofing. Site config picks the adapter(s).

**How it reaches the phone (important)**

Raw HL7 v2 MLLP from a mobile app is rarely acceptable to hospital IT. Preferred patterns:

1. **FHIR over HTTPS** (direct to EPD FHIR gateway or API management) — preferred long-term
2. **HL7 v2 via site interface engine** — engine speaks MLLP upstream; app calls a small HTTPS façade (hospital-hosted or our optional on-prem connector) that performs QBP/ADT-style lookup and returns demographics JSON
3. **IHE PDQ / PDQm** where the site already has a PIX/PDQ actor

The Android app always sees a clean “lookup by identifier → patient demographics (+ optional orders)” API; transports stay pluggable.

| Concern | Near-term (Phases 1–2) | Next (Phase 5 identity) | Forward |
|---|---|---|---|
| Who is the patient? | Manual / MWL / barcode | **HL7 v2 query** and/or **FHIR Patient** search | SMART launch + PDQm |
| Which order/exam? | MWL + PACS C-FIND | FHIR `ServiceRequest` / `ImagingStudy` where available | Same |
| Where do images go? | DICOM C-STORE / STOW-RS → PACS | Still DICOM | Still DICOM |

Keep demographics source-of-truth at the EPD; the app is a **modality + lookup client**, not an MPI.

### IHE (target actors)

| Profile | Actor intent |
|---|---|
| Radiology **Scheduled Workflow (SWF)** | Acquisition Modality (MWL → acquire → store) |
| **Consistent Presentation / patient ID** hygiene | Prefer EHR/MWL demographics over free-text |
| Radiology **Web-based Image Capture (WIC)** | Optional path: mobile capturer → Image Manager via DICOMweb |
| ITI **PDQ / PDQm** | Patient Demographics Query (v2 / FHIR) when site supports it |
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
┌──────────────────────────────────────────────────────────────────┐
│  Android app (Kotlin)                                            │
│  UI → Identity resolve → Capture → DICOM encode → Store → Purge│
│         │                  │            │                        │
│    PatientDirectory     CameraX    DICOM toolkit                 │
│    (MWL | HL7 | FHIR)                                            │
└─────────┬──────────────────┴────────────┬────────────────────────┘
          │                               │
   EHR identity lookups            PACS imaging
   FHIR HTTPS / HL7 façade         DIMSE + DICOMweb
          │                               │
          ▼                               ▼
   EPD / interface engine          Any standards-compliant PACS/VNA
          │                               ▲
          └──────── MWL SCP (RIS/broker) ─┘
```

**Stack leanings (to validate in Phase 0):**

- UI: Kotlin + Jetpack Compose + CameraX
- DICOM: native DCMTK via NDK **or** pure-JVM dcm4che — pick one primary stack in Phase 0 spike
- Identity: internal interfaces first; stub manual + MWL; reserve HL7 v2 façade + FHIR R4 clients
- Local test harness: Orthanc and/or dcm4chee + sample MWL; later HAPI FHIR + HL7 test façade
- Config: encrypted SharedPreferences / DataStore; MDM managed configurations

**Non-goals for architecture:** our cloud must not become the image archive. Optional **on-prem identity connector** (HL7 v2 MLLP ↔ HTTPS) is allowed when hospitals need it; images still go device → PACS.

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
- Define `PatientDirectory` / `OrderDirectory` interfaces (manual, MWL, future HL7, future FHIR) so we do not paint ourselves into a DICOM-only identity corner

**Exit criteria:** Hello-PACS demo on a device/emulator; written ADR for DICOM stack; DPIA outline + intended-purpose draft exists; identity interfaces sketched.

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

### Phase 5 — EHR identity (HL7 + FHIR) + NL/EU compliance packaging

**Goal:** Resolve patient/order details from the EPD on the fly; pilot-ready for Dutch/EU IT, security, and privacy review.

**Identity (planned capability — not an afterthought)**

- Barcode/QR of Patient ID or Accession as the usual trigger
- **HL7 v2 demographics query** via hospital interface-engine façade (QBP/RSP or site-equivalent) → fill Patient ID, name, DOB, sex, etc. before capture
- **FHIR R4** `Patient` search/read; optional `ServiceRequest` / `ImagingStudy` for order/exam context; SMART-on-FHIR launch when the EPD supports it
- Adapter selection per site (HL7, FHIR, or both); map results into the same DICOM patient/study tags used for Store
- Optional thin **on-prem connector** repo/docs for MLLP↔HTTPS if the site has no FHIR gateway
- Unscheduled / emergency workflow (generated UIDs under local policy) when no order exists

**Compliance & ops**

- Role-based access (operator vs admin config)
- Privacy UX: clear “niet blijvend op dit apparaat” messaging; MDM remote wipe assumptions
- Compliance pack: DPIA/GEB (include EHR lookup flows), verwerkersovereenkomst template, NEN 7510 questionnaire answers, SBOM, versioned releases, test evidence
- MDR pathway decision recorded with counsel; QMS artifacts if classified as device

**Exit criteria:** At least one HL7 lookup path and one FHIR Patient lookup path demoed against test harnesses; demographics correctly stamped into stored DICOM; NL-oriented privacy/security pack complete.

---

## Suggested module map (implementation)

```
app/
  ui/          # Compose screens: worklist, lookup, capture, review, settings
  capture/     # CameraX photo/video
  dicom/       # Encode SOP instances, UID generation
  network/     # DIMSE + DICOMweb clients
  identity/    # PatientDirectory/OrderDirectory: manual, MWL, HL7, FHIR
  ehr/         # HL7 façade client + FHIR R4 client (Phase 5)
  security/    # crypto, wipe, secure staging
  audit/       # local audit trail
  config/      # PACS nodes, EHR endpoints, MDM
```

Optional companion (Phase 5): `connector/` — on-prem HL7 v2 MLLP ↔ HTTPS demographics service for sites that need it.

---

## Testing strategy

| Layer | What |
|---|---|
| Unit | Tag builders, UID rules, wipe guarantees, query filters |
| Integration | Orthanc + MWL SCP in CI/docker; later HAPI FHIR + HL7 façade fixtures |
| Device | CameraX on real Android hardware; MDM config smoke |
| Conformance | Store/MWL/Find against validator / dciodvfy where applicable |
| Identity | HL7 and FHIR lookup contract tests; tag-mapping golden tests |
| Security | No MediaStore leakage; leftover file scan after kill/crash |

---

## Open decisions (resolve in Phase 0–1)

1. **DICOM toolkit:** DCMTK (NDK) vs dcm4che (JVM) vs hybrid
2. **Offline policy:** block capture when PACS unreachable vs short encrypted staging queue
3. **Video SOP class:** which encapsulated/multi-frame profile to standardize on
4. **Primary transfer syntax:** JPEG Baseline vs JPEG-LS vs uncompressed for photos
5. **MDR classification** for stated intended purpose (NL/EU counsel) — drives QMS depth
6. **Auth model:** AE-only LAN trust vs user login (OIDC/SAML) in MVP
7. **EHR identity order:** ship HL7 façade and FHIR Patient lookup in the same Phase 5 train (default: yes); which Dutch pilot EPD to target first for FHIR profiles (e.g. Nictiz-oriented)
8. **On-prem connector:** build our own thin HL7↔HTTPS service vs document “bring your own interface engine” only

---

## Phase priority for build start

Ship imaging value first: **Phase 0 → 1 → 2 → 3**, with Phase 4 overlapping Phase 2–3.  
**Design identity interfaces in Phase 0** so HL7 + FHIR plug in cleanly; **implement EHR on-the-fly lookup in Phase 5** (HL7 and FHIR both in scope). DPIA/MDR drafting starts in Phase 0 and must cover EHR query flows before real-patient pilots.

When Phase 0 starts, first concrete tasks:

1. Android project scaffold (Compose + CameraX)
2. Docker Orthanc (+ MWL) for local/CI
3. DICOM stack spike: Echo + Store one photo
4. Secure staging + wipe proof test
