# Security Policy

## Status of this project

**Arnout.pro DICOM Camera** is published for **laboratory evaluation, interoperability testing, and controlled pilot preparation**. Builds distributed from this repository (including `dist/*.apk`) are **not authorised for clinical use** until a deploying organisation completes applicable regulatory and privacy steps.

See [`DISCLAIMER.md`](DISCLAIMER.md).

This policy covers **security of the software and repository**. Clinical safety, MDR classification, and site DPIAs are separate obligations of the deploying zorginstelling.

## Supported versions

| Channel | Support |
|---|---|
| `main` (latest source) | Security fixes accepted here |
| Sideload APK in `dist/` | Lab preview only — rebuild from `main` for the newest fixes |
| Debug / staging flavors | Intended for lab; not a production support contract |

There is no long-term LTS branch yet. Prefer reporting against current `main`.

## What to report

Please report in good faith:

- Remote or local vulnerabilities in the Android app, `:dicom`, or `:identity` modules  
- Failures of wipe-after-store, staging isolation, or unintended gallery / backup exposure  
- Credential handling issues (Settings, MDM managed config, bearer tokens)  
- Insecure defaults that would be dangerous if copied into a hospital deploy  
- Supply-chain issues in dependencies that affect this project  

**Out of scope (please do not file as product vulns):**

- Misconfiguration of a lab Orthanc / PACS / EHR by the tester  
- “I pointed the app at production” without hospital controls  
- Social engineering of hospital staff  
- Issues that require physical access **and** an unlocked device with USB debugging already enabled, unless they escalate further  

## Do not include real patient data

Never attach PHI, real DICOM objects with patient identifiers, production credentials, or hospital VPN configs to a GitHub issue or PR.

Use synthetic patients, redacted logs, and lab Orthanc only.

## How to report a vulnerability

**Preferred:** open a **private** security advisory on GitHub:

→ [Security advisories for arnoutpro/dicomcamera](https://github.com/arnoutpro/dicomcamera/security/advisories/new)

If that is unavailable, open a **minimal public issue** titled `SECURITY: <short topic>` with **no** secrets or PHI, and ask for a private channel — or contact via [Arnout.pro](https://arnout.pro).

Include when possible:

1. Affected commit / version / APK `versionName`  
2. Impact (confidentiality / integrity / availability; PHI exposure?)  
3. Reproduction steps on a **lab** setup  
4. Suggested fix (optional)  

## Response expectations

This is a small open project. We aim to:

- Acknowledge reports when we see them  
- Triage severity (especially anything that could leave PHI on-device or leak credentials)  
- Fix on `main` and note the change in the PR / release notes when practical  

There is no paid bug bounty.

## Secure deployment reminders (operators)

- Prefer **MDM** managed configuration; lock operator Settings where possible  
- Use **TLS** to PACS/EHR; install hospital CAs via MDM  
- Register calling AE Title; restrict SCP access to the device subnet  
- Do not embed production bearer tokens in public forks or screenshots  
- Uninstall lab builds from devices that leave the lab  

More detail: [`docs/deploy/IT_DEPLOYMENT_GUIDE.md`](docs/deploy/IT_DEPLOYMENT_GUIDE.md) and [`docs/threat-model.md`](docs/threat-model.md).

## Dependency / supply chain

- Report known vulnerable transitive libraries with a path to upgrade  
- Do not commit secrets, keystores, or `local.properties`  
- Third-party licenses live under `docs/licenses/`
