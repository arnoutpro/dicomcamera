# IHE Web-based Image Capture (WIC) — path note

Optional alignment path when the site prefers DICOMweb over DIMSE for capture devices.

## Mapping

| WIC concept | DICOM Camera Phase 4 |
|---|---|
| Capture client | Android app (this product) |
| Store | **STOW-RS** (`TransportMode.DICOMWEB`) |
| Query prior study | **QIDO-RS** studies (append workflow) |
| Worklist | Still **DIMSE MWL** (or Phase 5 EHR); not WADO/UPS unless site provides UPS-RS later |

## Site enablement

1. Set managed config / settings: `pacs_transport=DICOMWEB`
2. Set `pacs_dicomweb_url` to the archive root (e.g. `https://pacs.example/dicom-web`)
3. Prefer HTTPS; install private CA via MDM so TLS trust matches hospital PKI
4. Set a dedicated MWL DIMSE destination if the worklist SCP is a different AE than the archive; otherwise leave MWL empty to reuse archive DIMSE

## Validation

- STOW of VL Photographic + Video Photographic instances
- QIDO by Patient ID / Accession returns the study used for append
- Confirm viewer/VNA renders encapsulated MPEG-4 video (see ADR 0002)
