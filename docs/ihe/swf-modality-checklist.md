# IHE Scheduled Workflow (SWF) — modality actor checklist

DICOM Camera targets the **Acquisition Modality** actor for photographic/clinical documentation (XC).

| Transaction | IHE | App support | Notes |
|---|---|---|---|
| Patient Registration / Order | → DSS/OF | External | Demographics via MWL or manual; EHR in Phase 5 |
| Procedure Scheduled | → MWL SCP | **MWL C-FIND SCU** | DIMSE; filters: date, modality, station AE, Patient ID, Accession |
| Modality Procedure Step In Progress / Completed | MPPS | Not claimed | Optional later; XC photo sessions often skip MPPS |
| Instance Availability / Store | → Image Manager | **C-STORE / STOW-RS** | VL Photographic + Video Photographic |
| Query images / studies | → Image Manager | **Study Root C-FIND / QIDO-RS** | Append-to-study workflow |

## Guarantees we document for pilots

1. Confirm patient banner before capture
2. One Series UID per capture session (photos + videos)
3. Wipe local pixels after successful store ACK
4. Audit trail for select / store events (exportable ATNA-style)

## Gaps / non-goals (Phase 4)

- MPPS N-CREATE/N-SET
- Storage Commitment
- Full Evidence Documents / KOS
