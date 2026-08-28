# HL7 demographics façade (MVP contract)

The Android app never speaks raw HL7 v2 MLLP. Hospitals expose an HTTPS façade
(interface engine or our optional connector) that performs QBP/RSP (or site-equivalent)
upstream and returns JSON.

## Settings

- **Enable** HL7 lookup
- **Base URL** e.g. `https://ehr-gw.hospital.local/hl7`
- **Bearer token** (optional)

## Request

```
GET {baseUrl}/patients?patientId={id}
Accept: application/json
Authorization: Bearer {token}   # if configured
```

Optional query params: `patientName`, `accessionNumber`.

## Response

Single patient:

```json
{
  "patientId": "123456789",
  "patientName": "JANSEN^ANNE",
  "birthDate": "19800315",
  "sex": "F"
}
```

Or list / wrapped:

```json
{ "patients": [ { "patientId": "...", "patientName": "..." } ] }
```

`patientName` should be DICOM PN style `FAMILY^GIVEN`.  
`birthDate` is DICOM DA `YYYYMMDD`.  
`sex` is `M` | `F` | `O`.

## App usage

Worklist tab → enter Patient ID → **Look up in EHR** → fields fill → continue to capture.

Lab mock (no MLLP): `lab/hl7-facade` on port `8090`. See `lab/README.md`.
