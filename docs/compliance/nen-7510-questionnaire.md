# NEN 7510 — questionnaire draft answers

Status: draft for security questionnaire responses. Validate with hospital CISO.

| Theme | Draft answer |
|---|---|
| Asset classification | Clinical documentation modality client; PHI ephemeral on device |
| Access control | Device MDM enrollment; optional operator config lock; AE Title registration on PACS |
| Authentication | LAN AE trust for DICOM; Bearer/mTLS for FHIR/HL7 façades; no shared gallery |
| Encryption in transit | HTTPS for EHR; DICOM TLS optional (system trust store / MDM CA) |
| Encryption at rest | App-private storage; OS disk encryption assumed via MDM policy |
| Logging / audit | Local audit CSV + ATNA-style export; diagnostic log opt-in |
| Malware / hardening | Managed devices; Play/enterprise distribution |
| Backup | `allowBackup=false`; no durable PHI archive on device |
| Supplier | Standards-based PACS/EHR only; no proprietary vendor lock-in |
| Incident | Wipe device via MDM; discard pending queue; notify per AVG |
| Privacy by design | Minimize demographics source (EHR > manual); wipe after ACK; privacy banner |
