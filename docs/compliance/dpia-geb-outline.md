# DPIA / GEB outline (Netherlands-first)

Status: outline only — complete with counsel/privacy officer before real-patient pilot.

## Processing overview

| Item | Draft answer |
|---|---|
| Processing | Capture clinical photos/videos; resolve patient identity; send to PACS; wipe device copy |
| Categories of data | Health data (clinical images), identifiers (patient ID, accession), technical logs |
| Data subjects | Patients of the zorginstelling |
| Verwerkingsverantwoordelijke | Zorginstelling (typical) |
| Verwerker | Vendor (for any telemetry/support); on-device processing under instruction of the zorginstelling |
| Retention on device | Until successful PACS acknowledgement, then wipe |
| Retention in PACS | Per zorginstelling policy (outside app control) |

## Necessity & proportionality

- Images are necessary for clinical documentation workflows replacing insecure channels (e.g. WhatsApp)
- Minimization: no gallery storage; no durable local archive; demographics from EHR/MWL when possible
- Alternative considered: tethered hardware cameras — rejected for mobility / cost; insecure messaging — rejected for AVG/NEN risk

## Risks (high level)

1. Residual PHI on lost/stolen device → encryption + wipe + MDM remote wipe
2. Misfile to wrong patient → identity confirmations + MWL/EHR lookup
3. Unauthorized network access → TLS/VPN, AE authn, later user auth
4. Over-collection via verbose logs → audit without pixel payloads

## Measures

- Technical: app-private staging, secure delete, no backup, TLS paths, audit
- Organizational: verwerkersovereenkomst, NEN 7510 questionnaire, access via hospital accounts, DPIA sign-off before pilot
- Rights: handled by zorginstelling; app supports purge

## Residual risk

To be scored after pilot architecture freeze (Phase 4–5).
