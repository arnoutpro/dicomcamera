# Sideload builds (`dist/`)

Prebuilt **Android APKs** for lab colleagues who want to try **Arnout.pro DICOM Camera** without cloning the whole repo or wiring Android Studio.

> **Lab / pilot only — not for clinical use.**  
> Debug-signed preview builds. No CE mark. No signed DPIA. Do **not** capture real patients.  
> Full text: [`DISCLAIMER.md`](../DISCLAIMER.md) · License: [`LICENSE`](../LICENSE) (Apache 2.0)

---

## What’s in this folder

| File | Purpose |
|---|---|
| `dicomcamera-staging-debug.apk` | Installable **staging** debug APK (the one you sideload) |
| `README.md` | This guide |

| | Current APK |
|---|---|
| **App label** | Arnout.pro DICOM Camera |
| **Package** | `nl.dicomcamera.app.staging` |
| **Version** | `0.6.10-security-audit-staging` (`versionCode` 19) |
| **Min / target SDK** | 26 / 35 (Android 8.0+) |
| **Signing** | Android **debug** keystore (not a release / Play key) |
| **Flavor** | `staging` — empty PACS defaults; you configure Settings (or MDM later). Cleartext HTTP blocked (use DIMSE or HTTPS for DICOMweb/EHR; **dev** flavor still allows lab Orthanc HTTP). |

CI also uploads fresh debug APKs as workflow artifacts on pushes/PRs (`dicomcamera-debug-apks`). Prefer those if you need a build newer than the file committed here.

---

## Install (sideload)

### Option A — `adb` (recommended)

1. On the phone: **Developer options** → enable **USB debugging**.  
2. Connect USB (or use wireless debugging).  
3. From the repo root (or wherever you saved the APK):

```bash
adb devices
adb install -r dist/dicomcamera-staging-debug.apk
```

`-r` replaces an older install of the **same** package id.

### Option B — file copy (no PC toolchain)

1. Copy `dicomcamera-staging-debug.apk` to the phone (Drive, USB, secure share — your IT policy).  
2. Open the file in Files / Downloads.  
3. Allow **Install unknown apps** for that source if Android prompts.  
4. Install → open **Arnout.pro DICOM Camera**.

You should see a gold strip under the top bar: **Lab / pilot only — not for clinical use**.

### Uninstall

```bash
adb uninstall nl.dicomcamera.app.staging
```

Or long-press the app icon → Uninstall.

---

## First launch checklist

1. Grant **Camera** (and follow prompts if the system camera path is used).  
2. Confirm the lab banner is visible.  
3. Open **Settings** → enter your lab PACS (DIMSE and/or DICOMweb).  
4. Run connectivity test (C-ECHO / DICOMweb).  
5. Prefer **Worklist** or demo patients — avoid typing real demographics.

### Typical Orthanc lab values (DIMSE)

| Setting | Example |
|---|---|
| Transport | DIMSE |
| Host | Phone on same LAN as Orthanc host IP (emulator: `10.0.2.2`) |
| Port | `4242` (common Orthanc DICOM port) |
| Called AE | `ORTHANC` |
| Calling AE | `DICOMCAM` (must be allowed on the SCP) |

DICOMweb / OHIF / MWL notes: [`lab/README.md`](../lab/README.md).  
Hospital MDM / TLS / AE registration: [`docs/deploy/IT_DEPLOYMENT_GUIDE.md`](../docs/deploy/IT_DEPLOYMENT_GUIDE.md).

---

## What this build can do (smoke path)

- Worklist (MWL), append-to-study, manual / emergency patient  
- Photo + video in one exam session → DICOM encode → PACS store  
- Pending retry queue if store fails  
- Wipe of local pixels after successful PACS ACK  
- Settings → About: intended purpose, AVG posture, MDR/DPIA lab warning  

It is **not** a finished hospital rollout package (no release signing, no Play distribution, no MDR/CE).

---

## Hard rules for lab use

| Do | Don’t |
|---|---|
| Use demo / synthetic patients | Use real patient photos or PHI |
| Point at lab Orthanc / test PACS | Point at production PACS “just to try” |
| Keep devices on a lab VLAN if possible | Commit credentials or dumps into git |
| Uninstall when the trial ends | Treat debug APKs as a clinical archive |

---

## Permissions (declared)

- `CAMERA` — capture  
- `INTERNET` / `ACCESS_NETWORK_STATE` — PACS / EHR  

No gallery write path: staging is app-private; successful store wipes local pixels.

---

## Rebuild / refresh this APK

After app changes that should ship in `dist/`:

```bash
./gradlew :app:assembleStagingDebug
cp -f app/build/outputs/apk/staging/debug/app-staging-debug.apk \
  dist/dicomcamera-staging-debug.apk
```

Bump `versionCode` / `versionName` in `app/build.gradle.kts` when you publish a new sideload drop so phones and humans can tell builds apart.

**Dev flavor** (emulator Orthanc defaults at `10.0.2.2`):

```bash
./gradlew :app:assembleDevDebug
# → app/build/outputs/apk/dev/debug/
# package: nl.dicomcamera.app.dev
```

Dev APKs are usually **not** committed here; use CI artifacts or a local build.

---

## Troubleshooting

| Symptom | Likely fix |
|---|---|
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Uninstall the old package first, or install over the same debug-signed build |
| App installs but PACS fails | Host/port/AE, firewall, phone must reach Orthanc IP (not `localhost` on device) |
| Camera denied | Re-grant in system App info → Permissions |
| “Unknown sources” blocked | IT policy / work profile — use MDM deploy or a lab profile |
| Confused which APK you have | Check Settings → About version, or `aapt dump badging … \| head` |

---

## Related docs

| Doc | Why |
|---|---|
| [`../DISCLAIMER.md`](../DISCLAIMER.md) | Lab-only / liability |
| [`../README.md`](../README.md) | Product overview |
| [`../lab/README.md`](../lab/README.md) | Orthanc + EHR harnesses |
| [`../docs/deploy/IT_DEPLOYMENT_GUIDE.md`](../docs/deploy/IT_DEPLOYMENT_GUIDE.md) | Network, MDM, verification |
| [`../docs/compliance/DICOM_CONFORMANCE_STATEMENT.md`](../docs/compliance/DICOM_CONFORMANCE_STATEMENT.md) | DICOM behaviour |

**Project:** [arnoutpro/dicomcamera](https://github.com/arnoutpro/dicomcamera) · **Brand:** [Arnout.pro](https://arnout.pro)
