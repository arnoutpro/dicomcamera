# Preview builds

Sideload these **debug-signed** APKs for UI smoke testing only — not for clinical use.

| File | Flavor | Notes |
|---|---|---|
| `dicomcamera-staging-debug.apk` | staging (Phase 1) | Empty PACS defaults; configure in-app (gear). Manual patient → capture → review → store → wipe. |

```bash
adb install -r dist/dicomcamera-staging-debug.apk
```
