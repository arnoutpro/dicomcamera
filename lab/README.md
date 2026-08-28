# Local lab — Orthanc PACS + EHR identity harnesses

Unit tests use an **in-process dcm4che Store SCP** (no Docker required).

For device / emulator demos:

```bash
cd lab
docker compose up -d --build
./demo-ehr-lookup.sh
```

`demo-ehr-lookup.sh` waits for HAPI (first boot is slow) and for `fhir-seed` to finish.

## Services

| Service | Port | Purpose |
|---|---|---|
| Orthanc | `4242` DIMSE / `8042` HTTP | PACS store / query |
| HAPI FHIR R4 (`hapiproject/hapi:v7.4.0`) | `8080` | FHIR `Patient` search |
| HL7 façade mock | `8090` | `GET /patients?patientId=` JSON (see `docs/deploy/HL7_FACADE_CONTRACT.md`) |

Stop:

```bash
docker compose down
```

## Orthanc (imaging)

| Setting | Value |
|---|---|
| DICOM host | `localhost` (emulator: `10.0.2.2`) |
| DICOM port | `4242` |
| AE Title | `ORTHANC` |
| HTTP UI | http://localhost:8042 |

App **dev** flavor defaults: `10.0.2.2:4242` / `ORTHANC` / `DICOMCAM`. Leave **MWL** empty in Settings to reuse this Orthanc node for worklist, or set a dedicated MWL host/AE when the SCP is separate.

## FHIR + HL7 demo (Phase 5)

Seeded sample IDs:

| Path | Patient ID | Expected name |
|---|---|---|
| FHIR | `999888777` | `de Vries^Jan` |
| FHIR | `444333222` | `Visser^Sara Maria` |
| HL7 | `123456789` | `JANSEN^ANNE` |
| HL7 | `555666777` | `DE BOER^KEES` |

### App settings (emulator)

Use a **dev** flavor APK. Staging/release block cleartext HTTP, so `http://10.0.2.2:…` EHR URLs will fail there.

Settings → **EHR identity**:

| Field | FHIR-only demo | HL7-only demo |
|---|---|---|
| Enable FHIR | on | off |
| FHIR base URL | `http://10.0.2.2:8080/fhir` | — |
| Enable HL7 | off | on |
| HL7 base URL | — | `http://10.0.2.2:8090` |
| Lookup mode | `FHIR_ONLY` | `HL7_ONLY` |

On a **physical phone** on the same LAN, replace `10.0.2.2` with your lab host LAN IP.

### Phone steps

1. Worklist → **Manual**
2. Enter Patient ID (`999888777` or `123456789`)
3. Tap **Look up in EHR**
4. Confirm name / DOB / sex filled
5. Continue → capture → store to Orthanc

### Curl (same checks as `./demo-ehr-lookup.sh`)

```bash
curl -s -H 'Accept: application/fhir+json' \
  'http://127.0.0.1:8080/fhir/Patient?identifier=999888777'

curl -s 'http://127.0.0.1:8090/patients?patientId=123456789'
```

Re-seed FHIR after wiping the HAPI container:

```bash
docker compose run --rm fhir-seed
```

## Mirth Connect (hospital façade)

Imaging **store stays Orthanc** (phone → C-STORE). For a real HL7 demographics façade on Mirth (same JSON contract as `hl7-facade`), see:

- [`docs/deploy/MIRTH_CHANNELS.md`](../docs/deploy/MIRTH_CHANNELS.md)  
- Transformer samples: [`mirth/`](mirth/)
