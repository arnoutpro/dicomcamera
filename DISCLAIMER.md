# Disclaimer — Arnout.pro DICOM Camera

**This software is provided for laboratory evaluation, interoperability testing, and controlled pilot preparation only.**

It is **not** authorised for clinical use on real patients until your organisation has completed the legal and regulatory steps that apply in your jurisdiction (for example EU MDR classification / CE marking where required, a signed DPIA/GEB, a verwerkersovereenkomst, and validation against your production PACS/EHR).

## Not a CE-marked medical device (current status)

- This repository and typical debug/lab builds are **research / engineering artefacts**.
- Do **not** treat the app as a finished medical device, a diagnostic aid, or a substitute for your hospital’s approved imaging workflow.
- In-app banners and Settings → About repeat this status so operators cannot miss it.

## No clinical decision support

The product is intended (when properly cleared and deployed) for **clinical photo and video documentation**: bind media to the correct patient/order, encode as DICOM, and send to PACS. It is **not** intended for diagnosis, triage, autonomous clinical decision-making, or long-term storage of health data on the device.

## Data protection

- Captures are staged in app-private storage and wiped after a successful PACS acknowledgement (when that path succeeds).
- Prefer worklist / EHR lookup over typing demographics by hand.
- Real patient data and production credentials must **never** be committed to this repository or used in public demos.

## Warranty and liability

THE SOFTWARE IS PROVIDED **“AS IS”**, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NON-INFRINGEMENT.

TO THE MAXIMUM EXTENT PERMITTED BY LAW, THE AUTHORS AND COPYRIGHT HOLDERS SHALL NOT BE LIABLE FOR ANY CLAIM, DAMAGES, OR OTHER LIABILITY ARISING FROM USE OF THE SOFTWARE — INCLUDING ANY USE WITH PATIENT DATA, ANY FAILED OR PARTIAL PACS STORE, OR ANY REGULATORY NON-COMPLIANCE BY A DEPLOYING ORGANISATION.

Deploying organisations remain solely responsible for:

1. Regulatory classification and market placement (e.g. MDR)  
2. Privacy impact assessment and controller/processor contracts (AVG/GDPR)  
3. Clinical safety validation, second-PACS testing, and operator training  
4. Network, MDM, and credential security in their environment  

## License

Source code is licensed under the **Apache License 2.0** (`LICENSE`).  
Third-party fonts and libraries retain their own licenses (see `docs/licenses/`).

## Contact

Project: [arnoutpro/dicomcamera](https://github.com/arnoutpro/dicomcamera)  
Brand: [Arnout.pro](https://arnout.pro)
