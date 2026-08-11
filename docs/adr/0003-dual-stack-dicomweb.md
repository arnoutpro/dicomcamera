# ADR 0003: Dual-stack DIMSE + DICOMweb

## Status

Accepted for Phase 4.

## Context

Hospital PACS vary: classic DIMSE AE titles remain common; many VNAs and Orthanc-class systems expose DICOMweb (QIDO-RS / STOW-RS). Sites need a selectable transport without forking the app.

## Decision

- Expose **TransportMode**: `DIMSE` | `DICOMWEB` in settings and MDM managed config
- **DIMSE:** C-ECHO, C-STORE, MWL C-FIND, Study Root C-FIND (existing PacsClient)
- **DICOMweb:** HTTP ping, QIDO-RS studies, STOW-RS store (`DicomWebClient`)
- **MWL** remains DIMSE-only (IHE SWF). In DICOMweb mode, worklist uses DIMSE fallback when host/AE are still configured; otherwise show a clear error and prefer Append via QIDO
- Unified entry point: `PacsGateway`

## Consequences

- One codebase serves both stacks; IT chooses per site
- Conformance Statement lists both DIMSE and DICOMweb SCU roles
- Auth for DICOMweb (Bearer / basic) deferred until a pilot requires it
