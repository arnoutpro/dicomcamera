# Mirth lab samples — HL7 façade for DICOM Camera

These snippets are for a **minimal Mirth Connect** channel that implements
[`docs/deploy/HL7_FACADE_CONTRACT.md`](../../docs/deploy/HL7_FACADE_CONTRACT.md).

**Imaging store stays Orthanc** (phone → DIMSE/STOW). Do not replace that with Mirth.
Full topology: [`docs/deploy/MIRTH_CHANNELS.md`](../../docs/deploy/MIRTH_CHANNELS.md).

## Files

| File | Use in Mirth |
|---|---|
| `transformer-lab-static.js` | Destination transformer — lab demo without EPD (IDs match `lab/hl7-facade`) |
| `transformer-response-map.js` | Map Source Map / channelMap fields → façade JSON |
| `filter-patients-get.js` | Optional source filter for `/patients` |

## Channel sketch

1. Source: **HTTP Listener**, context `/dicomcamera`, method GET  
2. Destination: **JavaScript Writer** (lab) or **LLP Sender** (real QBP)  
3. Response: Destination 1 → HTTP body  

### Lab static destination (fastest demo)

Paste `transformer-lab-static.js` into a **JavaScript Writer** destination (or Destination transformer that sets `msg` / response).

Then:

```bash
curl -sS 'http://127.0.0.1:<mirth-http-port>/dicomcamera/patients?patientId=123456789'
```

Expect Anne Jansen demographics. Unknown id → `{"patients":[]}`.

### App Settings (emulator → Mirth on host)

| Field | Value |
|---|---|
| Enable HL7 | on |
| HL7 base URL | `http://10.0.2.2:<port>/dicomcamera` |
| Lookup mode | `HL7_ONLY` |

Use a **dev** APK (staging blocks cleartext HTTP).

## Real EPD (replace static JS)

1. Destination: LLP Sender → interface engine / PAS  
2. Build QBP^Q22 (or site equivalent) from `patientId`  
3. On RSP, map PID demographics into the JSON shape in `transformer-response-map.js`  
4. Keep Orthanc as the only C-STORE target for the phone

## Orthanc store (unchanged)

```text
DICOM Camera  --C-STORE-->  Orthanc :4242  AE ORTHANC
```

No Mirth hop.
