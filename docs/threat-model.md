# Threat model (Phase 0 draft)

## Assets

- Patient demographics (ID, name, DOB, sex)
- Clinical photos/videos (pixels + DICOM metadata)
- PACS/EHR connection config (hosts, AE Titles, later credentials)
- Audit events (who stored what study UID when)

## Entry points

- Device camera / microphone
- DICOM DIMSE to PACS (and later DICOMweb HTTPS)
- Later: HL7 façade HTTPS, FHIR HTTPS
- MDM-managed configuration
- Local encrypted staging directory (app-private)

## Trust boundaries

| Zone | Trust |
|---|---|
| Android app process | Least privilege; no gallery writes |
| Hospital LAN / VPN | Assumed for DIMSE; TLS preferred |
| PACS / VNA | System of record for images |
| EPD / interface engine | System of record for identity |
| Our cloud (if any) | Must not archive images |

## Key threats & mitigations

| Threat | Mitigation |
|---|---|
| PHI left on device after send | Secure overwrite + delete; purge on launch; no MediaStore |
| Gallery / backup leakage | App-private staging only; `allowBackup=false` |
| Wrong patient association | Confirm banner (Phase 2+); prefer MWL/EHR over manual |
| MITM on network | DICOM TLS / VPN; HTTPS for FHIR/HL7 façade |
| Malicious config | MDM-locked endpoints; restrict admin settings |
| Crash leaves staging files | Startup wipe of orphaned staging |
| Insider exfiltration via share sheet | No share intents for clinical pixels in MVP |

## Data flow (happy path)

```
Camera → encrypted/app-private staging → DICOM encode → C-STORE → wipe staging
                ↑
         PatientDirectory (manual → MWL → HL7/FHIR)
```

## Open Phase 0 items

- Confirm offline policy (block vs short staging)
- Certificate pinning strategy for DICOMweb/FHIR
- Audit log retention on device vs export-only
