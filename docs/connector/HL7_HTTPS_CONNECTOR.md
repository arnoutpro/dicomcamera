# On-prem HL7 v2 ↔ HTTPS connector (optional)

Status: design note for Phase 5. Build only if the site has no FHIR gateway and cannot expose an existing interface-engine HTTPS façade.

## Why

Raw HL7 v2 MLLP from a phone is rarely acceptable to hospital IT. The Android app always calls HTTPS. Sites that only speak MLLP upstream need a thin bridge.

## Recommended pattern

```
Phone (HTTPS/JSON)
    → hospital connector (this service)
        → MLLP QBP^Q22 / ADT query to interface engine
        ← RSP / demographics
    ← JSON { patientId, patientName, birthDate, sex }
```

Contract matches `Hl7PatientDirectory`:

`GET /patients?patientId={id}` → JSON object or `{ "patients": [ … ] }`.

## Minimal responsibilities

1. Terminate TLS with a hospital certificate
2. Authenticate the app (Bearer / mTLS)
3. Map Patient ID → QBP (or site-equivalent)
4. Map response demographics → JSON (DICOM PN `FAMILY^GIVEN`, DA `YYYYMMDD`, sex `M|F|O`)
5. No pixel data; no durable PHI beyond short-lived logs under IT policy

## Deployment

- Prefer hospital-hosted VM/container next to the interface engine
- Allowlist connector IP on the engine; allowlist app VPN/MDM path to the connector
- Document retention of connector logs in the site DPIA

## Alternative

“Bring your own” façade: many Dutch hospitals already expose an API management layer. Prefer that over deploying this connector.
