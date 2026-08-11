# IT deployment guide (draft)

Hospital / EPD IT guide for piloting **DICOM Camera** (Android).

## Architecture

```
Android device (MDM)
  → DIMSE (C-ECHO / MWL / C-FIND / C-STORE) and/or
  → DICOMweb (QIDO-RS / STOW-RS)
  → PACS / VNA
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

When restrictions are present, the app treats settings as **MDM-managed** (UI read-only).

## Flavors

- **dev** — emulator defaults (`10.0.2.2` Orthanc)
- **staging** — empty defaults; expect MDM or manual IT entry

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

## Second PACS

Exit criterion: validate against Orthanc (lab) **and** one commercial/VNA endpoint using the same AE/DICOMweb settings pattern. Record evidence in the pilot folder (AE config screenshots, sample SOP UIDs, viewer proof).
