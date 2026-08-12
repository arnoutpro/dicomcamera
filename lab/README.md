# Local lab — Orthanc PACS + EHR identity harnesses

Phase 0 unit tests use an **in-process dcm4che Store SCP** (no Docker required).

For device / emulator demos:

```bash
cd lab
docker compose up -d --build
./demo-ehr-lookup.sh
```

## Services

| Service | Port | Purpose |
|---|---|---|
| Orthanc | `4242` DIMSE / `8042` HTTP | PACS store / query / MWL (if plugins enabled) |
| HAPI FHIR R4 | `8080` | FHIR `Patient` search |
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

App **dev** flavor defaults: `10.0.2.2:4242` / `ORTHANC` / `DICOMCAM`.

## FHIR + HL7 demo (Phase 5)

Seeded sample IDs:

| Path | Patient ID | Expected name |
|---|---|---|
| FHIR | `999888777` | `de Vries^Jan` |
| FHIR | `444333222` | `Visser^Sara Maria` |
| HL7 | `123456789` | `JANSEN^ANNE` |
| HL7 | `555666777` | `DE BOER^KEES` |

### App settings (emulator)

Settings → **EHR identity**:

| Field | FHIR-only demo | HL7-only demo |
|---|---|---|
| Enable FHIR | on | off |
| FHIR base URL | `http://10.0.2.2:8080/fhir` | — |
| Enable HL7 | off | on |
| HL7 base URL | — | `http://10.0.2.2:8090` |
| Lookup mode | `FHIR_ONLY` | `HL7_ONLY` |

On a **physical phone** on the same LAN, replace `10.0.2.2` with your PC/Zima LAN IP (e.g. `192.168.1.200`).

Cleartext HTTP is allowed in the app network security config for lab use.

### Phone steps

1. Worklist → **Manual**
2. Enter Patient ID (`999888777` or `123456789`)
3. Tap **Look up in EHR**
4. Confirm name / DOB / sex filled
5. Continue → capture → store to Orthanc
6. Verify demographics on the stored instance in Orthanc Explorer

### Curl checks (same as `./demo-ehr-lookup.sh`)

```bash
curl -s -H 'Accept: application/fhir+json' \
  'http://127.0.0.1:8080/fhir/Patient?identifier=999888777'

curl -s 'http://127.0.0.1:8090/patients?patientId=123456789'
```

Re-seed FHIR after wiping the HAPI volume:

```bash
docker compose run --rm fhir-seed
```

## On Zima / NAS (same LAN as the phone)

If Orthanc already runs on the Zima, you can add the `fhir` + `hl7-facade` (+ `fhir-seed`) services from this compose next to it, or run a second compose project that only starts those two.

Phone Settings → EHR identity base URLs use the Zima LAN IP, e.g.:

- FHIR: `http://192.168.1.200:8080/fhir`
- HL7: `http://192.168.1.200:8090`
