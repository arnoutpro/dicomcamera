# ADR 0001: DICOM toolkit — dcm4che (JVM)

## Status

Accepted for Phase 0–2. Revisit before heavy video / NDK optimization work.

## Context

We need DICOM network (C-ECHO, C-FIND, C-STORE) and object encoding on Android.
Candidates: **dcm4che** (pure JVM), **DCMTK** (native via NDK), hybrid.

## Decision

Use **dcm4che 5.31.x** (`dcm4che-core`, `dcm4che-net`) as the primary toolkit for Phase 0.

## Consequences

- Faster spike: no NDK/CMake toolchain required in CI for Hello-PACS
- Same library powers in-process test SCP (no Docker required for unit tests)
- Android packaging must exclude conflicting `META-INF` entries
- DICOMweb can be added later (HTTP client + dcm4che data model, or STOW via separate module)
- If video encoding / performance forces native codecs, reconsider DCMTK or a hybrid for pixel pipelines only

## DICOMweb timing

DIMSE first (Phases 0–2). DICOMweb (QIDO/STOW) spike targeted in Phase 4, with an earlier spike optional once Store path is stable.
