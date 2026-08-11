# Verwerkersovereenkomst — template outline

Status: template for legal review (NL). Not a signed agreement.

## Parties

- Verwerkingsverantwoordelijke: zorginstelling
- Verwerker: [vendor legal name] — only if vendor processes personal data outside the zorginstelling environment (support, crash telemetry). Pure on-prem MDM install with no vendor backhaul may be instruction-only.

## Subject matter

Clinical photographic/video documentation app that:

1. Resolves demographics via hospital FHIR / HL7 façade
2. Encodes DICOM and stores to hospital PACS
3. Wipes local copies after successful store

## Categories

Health data (images), identifiers (Patient ID, accession), technical audit metadata.

## Instructions

Processing only under documented instructions of the zorginstelling; no secondary use; no training on customer PHI.

## Security

Encryption in transit (TLS), app-private staging, secure wipe, MDM controls, audit export on request.

## Sub-processors

List any cloud build / crash / analytics vendors — default for clinical MVP: **none** for PHI.

## Breach notification

Align with AVG art. 33/34 timelines via the zorginstelling’s process.
