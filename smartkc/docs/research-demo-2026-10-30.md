# Eye Screening 0.8.0: supervised demonstration

Prepared 30 October 2026. This release is for the supervised prototype demonstration described in chat. It does not complete a validated keratoconus or dry-eye disease classifier.

## Install and show the keratoconus research output

1. Install `eye-screening-0.8.0-research-demo.apk` over the previous combined Eye Screening app. The application ID and debug signer are unchanged, so existing private captures can be retained.
2. Open Keratoconus, then **Saved photo captures**. Select capture **7808e5f2**, the original 3000x4000 photo you exported. Let its ring analysis finish.
3. Expand **Maps and calibration (optional)** and use **Open reconstructed maps** to choose `SIMULATION-capture-7808e5f2.zip`.
4. The app displays watermarked axial/tangential maps and a study-threshold flag labelled **Research simulation - assumed geometry**. The maps were calculated on the PC by the original SmartKC reconstruction, then imported into the phone. They were not calculated entirely on the phone.

The package is tied to the exact original image hash, capture ID and right-eye label. If capture 7808e5f2 is no longer present, export the source capture currently in the app so its result association can be regenerated; do not rename unrelated records or claim a reference example is your result.

### Meaning of this specific simulation

The known camera values are portrait sensor axes 4.8 x 6.4 mm, focal length 4.745 mm and 2x zoom. The original attachment STL files match the project. The simulation assumes nominal distance 75 mm, camera gap 4 mm and the study's fitted distance correction. Those optical distances have NOT been measured for the separately handheld setup.

At this preset, the software produced simulated K values 46.16 / 31.00 D and PPK algorithm score 1.00. That score is not a 100 percent probability that this person has keratoconus. The displayed flag means only that the original algorithm's reference-device threshold was crossed under these assumptions. Neither a triggered nor an untriggered flag is a diagnosis or a rule-out result.

The same photo was replayed with other assumed distance parameters:

| Assumed nominal parameter | Simulated K1 | Simulated K2 | PPK algorithm score |
|---|---:|---:|---:|
| 70 mm | 58.29 D | 38.68 D | 1.00 |
| 75 mm | 46.16 D | 31.00 D | 1.00 |
| 80 mm | 37.74 D | 25.64 D | 1.00 |

The large K variation demonstrates geometry sensitivity. The saturated score staying at 1.00 is NOT evidence of diagnostic robustness. This parameter sweep is an engineering experiment, not a confidence interval for the eye's real corneal measurements.

### What happened to the raw-image classifier candidate?

A calibration-free descriptor was implemented: ring residuals after removing low-order shape components, variation of ring centres, and variation of elliptical components. It was compared with the four project-labelled examples. On leaving one example out, two cases matched the wrong group and two were inconclusive. The candidate therefore failed its engineering audit. It is not enabled as a patient disease classifier. Self-matching the four examples is only a library/replay check, not independent accuracy evidence. `ring-comparison-audit.json` records the outcomes; `smartkc/audit_ring_comparison.py` reproduces the audit.

The live photo view reports observable ring-shape variation, reference distances and photo quality, with keratoconus status undetermined. No clinical accuracy percentage has been invented. No additional clinically labelled photos are available according to the user.

## Start the dry-eye module

1. Open Dry Eye and choose left/right eye.
2. Record a short Placido video, or use **Analyse a saved Placido video**. A roughly 15-second recording is a useful technical starting point. Use a comfortable blink and keep the reflection in view; stop if uncomfortable.
3. Analysis first looks for a ring pattern, uses a square region around it, and samples the video at requested 125 ms intervals. It looks for a close-and-reopen signal rather than accepting a brightness flash alone.
4. Output is an **experimental post-blink ring-signal interval**, a lower-bounded observation with no sustained change, or an inconclusive result. Motion, poor correlation, another blink, missing frames, insufficient baseline detail or short observation can prevent a timing result.

The mobile method is not the full desktop DEDector implementation and is not clinically validated NIBUT. Its interval may reflect focus, motion or illumination as well as tear-film change. No-change output does not establish that an eye is healthy. A real Placido video from the intended phone/attachment is still needed for testing the new native-phone flow.

The included 6-second synthetic sample is not patient data. CPU replay recognised its ring pattern, but the signal timeline remained inconclusive because fewer than 10 seconds after a candidate blink were available. Recognition is not an authenticity test of a living cornea.

## Verification and limits

- 60 Java unit tests passed: existing capture logic, source identity/mode separation, descriptor controls, quality abstention, real elapsed-time bookkeeping, blink-pair tests, transient versus sustained changes, and motion checks.
- 17 Python unit tests passed, including strict-default calibration/crop validation and checksum protection when the explicitly opted-in simulation confirms an original image.
- The simulator CLI ran end to end on the supplied original and produced watermarked PNGs, provenance JSON and an import ZIP. Three assumed-distance replays completed. The normal measured-geometry workflow still requires confirmed geometry.
- Android compilation and packaging succeeded. Signatures and model-asset integrity are checked separately at delivery.
- The new screens, native media decoding, ring-region selection and motion checks and saved-video picker have not yet been tested on the physical OnePlus 9R. CPU replays and synthetic timing tests do not establish clinical accuracy.
- All changes remain local in the existing checkout. Earlier unrelated working-tree changes were preserved. Nothing was pushed.

## Reproduce the simulation

From the repository root, using its installed SmartKC environment:

```
shared/environments/smartkc/Scripts/python.exe smartkc/simulate_capture.py --capture <capture.zip> --output <new-output-directory> --assumed-study-geometry --confirm-original --sensor-width-mm 4.8 --sensor-height-mm 6.4 --focal-length-mm 4.745 --zoom 2
```

`--confirm-original` records the user's explicit original-image confirmation when the app checkbox was left unchecked. It does not skip the source checksum check. The simulation has a distinct result mode, forces geometry_confirmed=false and cannot be imported as a measured result. Simulations and measured reconstructions are stored in separate folders in the phone's capture record.

## Primary sources and scope

- [Original SmartKC implementation](https://github.com/microsoft/SmartKC-A-Smartphone-based-Corneal-Topographer): the reconstruction components and original attachment design.
- [SmartKC classifier publication](https://www.microsoft.com/en-us/research/publication/keratoconus-classifier-for-smartphone-based-corneal-topographer/): its disease classifier takes reconstructed maps. This app's four-reference descriptor is not that classifier, and the publication's performance does not transfer to this prototype.
- [Classifier paper](https://arxiv.org/abs/2205.03702): the reported 0.20/0.45 PPK boundaries are Keratron reference-device conventions. Applying them to an assumed-geometry simulation does not establish validity for this person's eye.

For submission, describe the system as a functional research prototype with ring recognition, explicit geometry simulations and exploratory temporal signal analysis. A reliable person-level has/does-not-have classifier remains uncompleted.
