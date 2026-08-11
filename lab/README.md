# Local PACS lab (Orthanc)

Phase 0 uses an **in-process dcm4che Store SCP** in unit tests so CI works without Docker.

For manual device testing against a real PACS:

```bash
cd lab
docker compose up -d
```

| Setting | Value |
|---|---|
| DICOM host | `localhost` (emulator: `10.0.2.2`) |
| DICOM port | `4242` |
| AE Title | `ORTHANC` |
| HTTP UI | http://localhost:8042 |

Use the app **dev** flavor defaults (`10.0.2.2:4242` / `ORTHANC` / `DICOMCAM`).

Stop:

```bash
docker compose down
```
