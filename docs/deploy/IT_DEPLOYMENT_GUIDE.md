# IT deployment guide (draft)

Hospital / EPD IT guide for piloting **DICOM Camera** (Android).

## Architecture

```
Android device (MDM)
  → DIMSE archive (C-ECHO / C-FIND / C-STORE) and/or DICOMweb (QIDO-RS / STOW-RS)
  → DIMSE MWL (C-FIND) — dedicated worklist SCP, or archive DIMSE fallback
  → PACS / VNA + optional RIS/MWL broker
```

No durable clinical archive on the device after successful send. Ephemeral staging + pending retry queue only.

## Network

| Item | Guidance |
|---|---|
| Ports | DIMSE often `11112`/`104`/`4242`; DICOMweb HTTPS `443` (Orthanc lab `8042`) |
| AE Title | Default calling `DICOMCAM` — register on PACS |
| TLS | Enable `pacs_use_tls` for DIMSE; install hospital root/intermediate CA via MDM into the system trust store |
| Firewall | Allow device subnet → PACS; block general internet if policy requires |

## Android Managed Configurations

Restrictions schema: `app/src/main/res/xml/app_restrictions.xml`

| Key | Example |
|---|---|
| `pacs_transport` | `DIMSE` or `DICOMWEB` |
| `pacs_host` | `pacs.hospital.local` |
| `pacs_port` | `11112` |
| `pacs_called_aet` | `PACS` |
| `pacs_calling_aet` | `DICOMCAM` |
| `pacs_use_tls` | `true` |
| `pacs_dicomweb_url` | `https://pacs.hospital.local/dicom-web` |
| `mwl_host` | `mwl.hospital.local` (empty → use archive DIMSE) |
| `mwl_port` | `11112` |
| `mwl_called_aet` | `MWLSCP` |
| `mwl_use_tls` | `true` |
| `fhir_enabled` | `true` |
| `fhir_base_url` | `https://fhir.hospital.local/fhir` |
| `fhir_bearer_token` | (optional) |
| `hl7_enabled` | `true` |
| `hl7_base_url` | `https://ehr-gw.hospital.local/hl7` |
| `hl7_bearer_token` | (optional) |
| `identity_lookup_mode` | `FHIR_THEN_HL7` / `HL7_THEN_FHIR` / `FHIR_ONLY` / `HL7_ONLY` |

When restrictions are present, the app treats settings as **MDM-managed** (UI read-only).

Archive DIMSE (`pacs_host` / `pacs_port` / `pacs_called_aet`) is used for **C-STORE**, **C-ECHO**, and **Study FIND**. Modality Worklist is a **separate** DIMSE destination (`mwl_*`). If `mwl_host` and `mwl_called_aet` are left empty, MWL C-FIND falls back to the archive DIMSE node (typical for lab Orthanc). A partially filled MWL destination does not fall back — fill host, port, and called AE together.

## EHR identity (lab)

Local harnesses (HAPI FHIR + HL7 façade mock): see `lab/README.md`.

| App setting | Emulator | Physical device on LAN |
|---|---|---|
| FHIR base URL | `http://10.0.2.2:8080/fhir` | `http://<lab-host>:8080/fhir` |
| HL7 base URL | `http://10.0.2.2:8090` | `http://<lab-host>:8090` |

Sample IDs: FHIR `999888777`, HL7 `123456789`.

Cleartext HTTP EHR URLs work only on the **dev** flavor. Staging/release block cleartext; pilots should use HTTPS façades.

## Flavors

- **dev** — emulator defaults (`10.0.2.2` Orthanc); cleartext HTTP allowed for lab DICOMweb / EHR
- **staging** — empty defaults; expect MDM or manual IT entry; cleartext HTTP blocked

## Audit / SIEM

- Local CSV: app files `audit/audit.csv`
- ATNA-style export: Settings → **Export ATNA audit log** → `audit/atna/*.log` (RFC5424-ish lines)
- Pull via MDM file sync or USB forensics procedure — no automatic off-box ship in Phase 4

## Verification checklist

1. Connectivity test (C-ECHO or DICOMweb ping)
2. MWL item → capture photo+video → batch send → viewer shows both
3. Append via Study find / QIDO keeps Study Instance UID
4. After success, device staging folder empty
5. Forced failure → pending queue → retry succeeds
6. Manual Patient ID → Look up in EHR (FHIR and/or HL7) → demographics stamped on stored DICOM

## Second PACS

Exit criterion: validate against Orthanc (lab) **and** one commercial/VNA endpoint using the same AE/DICOMweb settings pattern. Record evidence in the pilot folder (AE config screenshots, sample SOP UIDs, viewer proof).
