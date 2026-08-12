# Preview builds

Sideload these **debug-signed** APKs for lab / UI smoke testing only — **not for clinical use**.
See [`DISCLAIMER.md`](../DISCLAIMER.md).

| File | Package | Notes |
|---|---|---|
| `dicomcamera-staging-debug.apk` | `nl.dicomcamera.app.staging` | Current staging build (lab banner, worklist/capture/PACS) |

```bash
adb install -r dist/dicomcamera-staging-debug.apk
```

Rebuild and refresh this folder after meaningful app changes:

```bash
./gradlew :app:assembleStagingDebug
cp -f app/build/outputs/apk/staging/debug/app-staging-debug.apk dist/dicomcamera-staging-debug.apk
```
