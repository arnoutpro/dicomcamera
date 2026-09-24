# Mirth Connect channels for Arnout.pro DICOM Camera

Minimal hospital interface-engine setup. **Imaging stays on Orthanc/PACS.** Mirth handles the **HL7 demographics HTTPS façade** the Android app already calls.

Comparable engines (Rhapsody, Cloverleaf, …) can implement the same façade contract.

## Topology (keep Orthanc store)

```
┌─────────────────────┐     DIMSE C-STORE / STOW-RS      ┌──────────────┐
│  DICOM Camera app   │ ───────────────────────────────► │ Orthanc/PACS │
│  (Android)          │     (VL Photographic + JPEG)     │  AE ORTHANC  │
└─────────┬───────────┘                                  └──────────────┘
          │
          │  GET /patients?patientId=…  (HTTPS JSON)
          ▼
┌─────────────────────┐     optional MLLP QBP/RSP        ┌──────────────┐
│  Mirth Connect      │ ───────────────────────────────► │ EPD / RIS /  │
│  HL7 façade channel │ ◄─────────────────────────────── │ interface DB │
└─────────────────────┘                                  └──────────────┘
```

| Path | Role | Mirth? |
|---|---|---|
| **Store (keep)** | Phone → Orthanc/PACS C-STORE or STOW-RS | **No** — do not put Mirth in the image path |
| **HL7 façade (this doc)** | Phone → Mirth HTTPS → demographics JSON | **Yes** |
| **MWL** | Phone → DIMSE C-FIND to Orthanc/RIS MWL SCP | **No** on the phone; Mirth may *feed* MWL upstream via ORM |

Lab Orthanc compose remains the imaging target: [`lab/README.md`](../../lab/README.md).

---

## Channel 1 — Orthanc / PACS store (**keep — not Mirth**)

Do **not** replace this with a Mirth DICOM listener for production.

| Setting (app) | Lab Orthanc example |
|---|---|
| Transport | DIMSE (or DICOMweb STOW) |
| Host | Orthanc LAN IP (`10.0.2.2` on emulator) |
| Port | `4242` |
| Called AE | `ORTHANC` |
| Calling AE | `DICOMCAM` |

SOP class: VL Photographic Image Storage `1.2.840.10008.5.1.4.1.1.77.1.4`  
Transfer syntax: JPEG Baseline `1.2.840.10008.1.2.4.50` (and/or Explicit VR LE)

Register calling AE `DICOMCAM` on Orthanc if required. Details: [`IT_DEPLOYMENT_GUIDE.md`](IT_DEPLOYMENT_GUIDE.md).

---

## Channel 2 — HL7 demographics façade (**Mirth**)

Implements [`HL7_FACADE_CONTRACT.md`](HL7_FACADE_CONTRACT.md).

### Behaviour

1. **Source:** HTTP Listener (HTTPS in production)  
2. **Filter:** only `GET` paths ending with `/patients` (or your chosen base path)  
3. **Transformer:** map `patientId` → upstream lookup → JSON  
4. **Response:** HTTP response map with JSON body and `Content-Type: application/json`

### App Settings

| Field | Example |
|---|---|
| Enable HL7 | on |
| HL7 base URL | `https://mirth.hospital.local/dicomcamera` |
| Bearer token | optional shared secret |
| Lookup mode | `HL7_ONLY` or `FHIR_THEN_HL7` |

Request the app sends:

```http
GET /dicomcamera/patients?patientId=123456789 HTTP/1.1
Accept: application/json
Authorization: Bearer <optional>
```

Expected JSON (DICOM-friendly demographics):

```json
{
  "patientId": "123456789",
  "patientName": "JANSEN^ANNE",
  "birthDate": "19800315",
  "sex": "F"
}
```

Empty result: `{ "patients": [] }` with HTTP 200 (app treats as no match).

### Minimal Mirth Admin steps

1. **Channels → New Channel**  
   Name: `DicomCamera-HL7-Facade`  
   Data types: Source **JSON** / Destination **JSON** (or Raw → JSON in transformer)

2. **Source connector → HTTP Listener**  
   - Context path: `/dicomcamera` (or `/`)  
   - Method: `GET`  
   - Response: **Destination 1** (or Source queue → Response)  
   - Enable HTTPS + hospital cert in production  
   - Optional: HTTP authentication / header check for `Authorization`

3. **Source Filter** (optional but useful)

```javascript
// Accept only demographics lookups
return msg.getHttpRequestUrl && (
  $('httpRequestUrl').indexOf('/patients') >= 0
);
```

If your Mirth version exposes URL differently, use Source Map keys from the message browser after one test call.

4. **Destination 1** — pick one:

| Mode | Destination | When |
|---|---|---|
| **A. Lab / no EPD yet** | JavaScript Writer that returns static/map JSON | Demo without MLLP |
| **B. Real site** | LLP Sender (QBP^Q22 or site query) → Response transformer builds JSON | Production |
| **C. DB** | Database Reader by patient id → JSON map | Sites with MPI view |

5. **Response** from Destination 1 → HTTP 200 + body.

Sample transformers: [`lab/mirth/`](../../lab/mirth/).

### Production notes

- Prefer **HTTPS**; staging APK blocks cleartext EHR HTTP (dev flavor allows lab HTTP).  
- No pixel data in this channel — demographics only.  
- Log retention / DPIA: treat Mirth logs like other interface-engine PHI.  
- Do not enable a Mirth DICOM Listener “just because” — keep Orthanc as SCP.

### Quick test (replace host)

```bash
curl -sS -H 'Accept: application/json' \
  'https://mirth.hospital.local/dicomcamera/patients?patientId=123456789'
```

App (dev / cleartext lab only):

```
HL7 base URL = http://<mirth-host>:8080/dicomcamera
```

Then Worklist → Manual → Patient ID → **Look up in EHR**.

---

## Optional later (not required)

| Idea | Recommendation |
|---|---|
| Mirth DICOM Listener → Orthanc | Skip for MVP; phone → Orthanc is simpler and more reliable |
| Mirth ORM → Orthanc MWL plugin | Useful when EPD sends orders; phone still does DIMSE MWL C-FIND |
| Mirth → Orthanc REST upload | Only if a non-DICOM system must drop images; not used by this app |

---

## Related

- Façade contract: [`HL7_FACADE_CONTRACT.md`](HL7_FACADE_CONTRACT.md)  
- Lab mock (no Mirth install): [`lab/hl7-facade`](../../lab/hl7-facade)  
- Transformer samples: [`lab/mirth`](../../lab/mirth)  
- Optional custom connector design: [`../connector/HL7_HTTPS_CONNECTOR.md`](../connector/HL7_HTTPS_CONNECTOR.md)
