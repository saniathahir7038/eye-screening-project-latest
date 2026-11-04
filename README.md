# Eye Screening Project

Research workspace for three smartphone-assisted eye-screening modules:

1. **SmartKC** — keratoconus screening from a Placido-ring eye image.
2. **DEDector** — dry-eye screening and NIBUT estimation from a Placido-ring eye video.
3. **Pterygium** — a MobileNetV2 research model and local screening interface for external-eye photographs.

## Repository structure

```text
eye-screening-project/
|-- smartkc/
|-- dedector/
|-- pterygium/
|-- test-data/
|-- results/
|-- shared/
`-- docs/
```

## Current status

- One combined Android hardware-validation APK now provides live and bundled-sample paths for Pterygium, Keratoconus/SmartKC, and Dry Eye/DEDector.
- **Latest Android version: `0.9.4-dry-eye-brightness-check`.** SmartKC runs ring segmentation, photo checks and optional corneal-map calculations on the phone. The OnePlus 9R preset uses explicitly assumed geometry. A new numerical fit check flags low-consistency maps and retains every attempt for debugging. The four-example disease-classifier candidate failed its audit and is not used for diagnosis. Source-linked desktop reconstruction and imported maps remain available.
- Dry Eye accepts recorded or saved Placido video, follows small translations after a candidate blink, and reports an experimental ring-signal interval, a partial usable observation or an inconclusive result. See [tracking details and supplied-video replay](docs/DRY-EYE-TRACKING.md). This mobile image-signal implementation is not the full desktop DEDector pipeline, validated NIBUT or a dry-eye diagnosis.
- See [the Android quick-use guide](pterygium/android_app/USAGE.md) for the current workflow and capability of each module.
- The official SmartKC computer pipeline was run successfully on all four bundled sample images.
- An offline SmartKC Android build mode was added because Microsoft's private Firebase configuration is not public.
- The offline SmartKC debug APK builds successfully, but live decisions still require physical phone, desktop-parity and Placido-hardware validation.
- The DEDector CLI and trained segmentation model load successfully on CPU.
- DEDector invalid-video handling was hardened and covered by three passing tests.
- The dry-eye brightness guard and report export were confirmed on the OnePlus 9R using the supplied video; independent replay matched the phone result. All 116 Java tests passed. Complete DEDector/mobile parity and clinical accuracy remain unvalidated.
- The Pterygium SLID audit, preprocessing, model training, locked evaluation, corrective validation iteration, Python interface, TensorFlow Lite export and standalone Android integration are complete. Physical-device and independent smartphone validation remain pending.

## Upstream projects

- [Microsoft SmartKC](https://github.com/microsoft/SmartKC-A-Smartphone-based-Corneal-Topographer)
- [Microsoft DEDector](https://github.com/microsoft/DEDector)

The upstream licences and attribution files are retained inside their respective module directories.

## Local-only content

Python environments, Android SDK files, generated builds, test recordings, APKs, and result outputs are intentionally excluded from Git. Recreate them locally using the module requirements and build configuration.

## Safety

This is an academic research prototype, not a medical diagnostic system. Human testing and patient-image collection require appropriate consent, privacy protection, clinical supervision, and institutional ethics approval.

### Offline phone maps and capture checks (0.9.1)

The combined Android app includes an optional **Generate maps on this phone** action for saved original OnePlus 9R captures. See [phone-map workflow and validation](docs/PHONE-MAPS.md). Maps use explicitly assumed geometry and remain experimental. See [the three-capture comparison and fit-check limits](docs/CAPTURE-CONSISTENCY.md) for the current consistency checks.
