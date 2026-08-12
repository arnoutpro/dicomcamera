# NL / EU compliance pack (Phase 5)

Living checklist for pilot readiness. Complete with counsel / privacy officer / CISO before real-patient use.

| Artifact | Path | Status |
|---|---|---|
| Intended purpose (MDR) | [intended-purpose-mdr-draft.md](intended-purpose-mdr-draft.md) | Draft |
| DPIA / GEB outline | [dpia-geb-outline.md](dpia-geb-outline.md) | Outline — extend for EHR lookup |
| DICOM Conformance Statement | [DICOM_CONFORMANCE_STATEMENT.md](DICOM_CONFORMANCE_STATEMENT.md) | **v1.0 technical** (PS3.2-style); Annex C notes UID + multi-PACS follow-ups |
| Verwerkersovereenkomst template | [verwerkersovereenkomst-template.md](verwerkersovereenkomst-template.md) | Template |
| NEN 7510 questionnaire answers | [nen-7510-questionnaire.md](nen-7510-questionnaire.md) | Draft answers |
| SBOM / release evidence | [sbom-and-test-evidence.md](sbom-and-test-evidence.md) | Process note |
| Privacy UX copy | Persistent lab-only banner + Settings → About (purpose, AVG, MDR/DPIA) | In app + `DISCLAIMER.md` |
| MDM remote wipe | Assumed via hospital MDM; app supports wipe-after-store | Documented in IT guide |
| On-prem HL7 connector | [../connector/HL7_HTTPS_CONNECTOR.md](../connector/HL7_HTTPS_CONNECTOR.md) | Optional |

## EHR lookup in the DPIA

Add as separate processing activity:

- Purpose: resolve Patient ID → name/DOB/sex before capture
- Sources: FHIR R4 Patient and/or HL7 façade (no MLLP on device)
- Recipients: zorginstelling EHR / interface engine only
- Retention on device: transient UI state; audit CSV without pixel data
