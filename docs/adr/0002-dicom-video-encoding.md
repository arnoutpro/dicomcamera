# ADR 0002: DICOM video encoding

## Status

Accepted for Phase 3.

## Context

Phase 3 requires clinical video capture on Android (CameraX typically produces MP4 / H.264).
We need a standards-based SOP class that PACS vendors commonly accept for photographic/clinical video.

## Decision

- **SOP Class:** Video Photographic Image Storage (`1.2.840.10008.5.1.4.1.1.77.1.4.1`)
- **Transfer Syntax:** MPEG-4 AVC/H.264 High Profile / Level 4.1 (`MPEG4HP41`)
- **Payload:** encapsulate the CameraX MP4 file bytes as DICOM Pixel Data (empty basic offset table + one fragment)
- **Modality:** `XC`
- Photos remain **VL Photographic Image Storage** + JPEG Baseline

## Consequences

- One video = one SOP instance in the current study/series session
- Some PACS prefer elementary H.264 vs full MP4 containers — validate per site in Phase 4; document fallback if needed
- PacsClient must negotiate Video Photographic Image Storage presentation contexts
- Conformance Statement lists Video Photographic Image Storage as SCU

## Alternatives considered

- Multi-frame True Color Secondary Capture — wider SC support but poor fit for continuous video
- Frame extraction to many VL photos — loses temporal fidelity and balloons object count
