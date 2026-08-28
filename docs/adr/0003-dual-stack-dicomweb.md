# ADR 0003: Dual-stack DIMSE + DICOMweb

## Status

Accepted for Phase 4.

## Context

Hospital PACS vary: classic DIMSE AE titles remain common; many VNAs and Orthanc-class systems expose DICOMweb (QIDO-RS / STOW-RS). Sites need a selectable transport without forking the app.

## Decision

- Expose **TransportMode**: `DIMSE` | `DICOMWEB` in settings and MDM managed config
- **DIMSE:** C-ECHO, C-STORE, MWL C-FIND, Study Root C-FIND (existing PacsClient)
- **DICOMweb:** HTTP ping, QIDO-RS studies, STOW-RS store (`DicomWebClient`)
- **MWL** remains DIMSE-only (IHE SWF) against a **dedicated MWL destination** (host/port/called AE). If those fields are empty, worklist falls back to the archive DIMSE node (lab Orthanc / existing MDM). In DICOMweb store mode, configure the MWL destination (or keep archive DIMSE filled as fallback); otherwise show a clear error and prefer Append via QIDO
- Unified entry point: `PacsGateway`

## Consequences

- One codebase serves both stacks; IT chooses per site
- Conformance Statement lists both DIMSE and DICOMweb SCU roles
- Auth for DICOMweb (Bearer / basic) deferred until a pilot requires it
