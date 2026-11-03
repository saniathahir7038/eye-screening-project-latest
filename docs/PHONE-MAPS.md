# Optional offline phone maps — 0.9.0

The combined Eye Screening app now includes the on-device calculation tested in Eye Map Lab 0.1.1. Open Keratoconus, choose or take an original photo, then tap **Generate maps on this phone**. The first visit starts calculation automatically. Revisiting restores the saved maps or failure report, with a button to calculate again.

This first implementation supports the confirmed OnePlus 9R demonstration preset: untouched portrait 3000 × 4000 photo at 2× zoom, sensor axes 4.8 × 6.4 mm, focal length 4.745 mm, original SmartKC ring dimensions, assumed nominal distance 75 mm, assumed camera setback 4 mm and the study's gap-correction curve. It does not establish measured geometry or a diagnosis. Other photo sizes remain usable for the existing ring-detection path but are rejected by this optional map screen. Android 8 / API 26 or later is required for this feature; the existing app minimum is unchanged.

## Preservation of the existing app

- Application ID remains `com.eyescreening.pterygium`; version code increases from 11 to 12, with the existing debug signer. Installing as an update retains app-private captures.
- Phone calculation reads the selected capture's original bytes. It writes only to `smartkc-captures/<capture UUID>/phone-maps/`. Existing source images, imported `simulation/` results and `measured-reconstruction/` results have separate locations.
- The new activity is not exported. Capture IDs and canonical paths are checked before opening a source or results folder.
- The existing detector is released before opening phone maps, and reloaded on return, to avoid retaining two model instances throughout calculation.
- Existing camera capture, photo import, sample replay, saved captures, desktop result import, dry-eye and pterygium paths remain present. Main-screen phone-map action is disabled while displaying a reference sample.
- The APK has no INTERNET permission. It retains the existing model assets unchanged and includes SmartKC attribution.

## Calculation and ring-support correction

The calculation follows the test app's bounded grayscale/colour and centre-refinement attempts, radial segmentation/graph corrections, Arc-Step reconstruction, degree-8 real Zernike fit and curvature-map rendering. All surface-information and fit gates are retained. Missing support is grey.

A legacy adjacent-ring check discarded a converged, observed point when the preceding ring was absent. Arc-Step already carries the last valid point across a gap. The port now retains that observed point when its own result is finite and passes the existing filters. Missing rings themselves remain absent.

On the actual successful phone report's saved label mask, this change increased retained surface points from 3,302 to 3,488 and supported directions from 194 to 198. The assumed radius and gap correction were unchanged. This is a small engineering improvement on one mask; it is not evidence of clinical accuracy or broad acquisition robustness. Desktop whole-pipeline pixel parity is not established.

## Validation and remaining device checks

80 Java tests pass, including the existing module tests, Arc-Step and Zernike reference comparisons, independent surface fixtures, actual phone-mask regression, insufficient-support rejection, and capture-record path/source preservation. The Android build, package identity, model asset hashes and signature are checked separately. Dry-eye source files and layout match the previously delivered 0.8.0 source archive byte for byte.

The standalone calculation was confirmed on the user's OnePlus 9R in 14.27 seconds. The integrated activity, model release/reload, automatic first run, per-capture restore and report sharing need the next phone test. Check taking/choosing a photo after returning from maps and opening the existing modules. No clinical accuracy has been evaluated.

**Share test report** exports the actual reconstruction crop, label mask, ring overlay, maps when available, per-attempt details, capture UUID, source checksum and error trace on failure. The full original photo is not included. Source correspondence must be checked before comparing different reports.

## Local build

Use the existing JDK 11 / Android SDK 33 / Gradle 7.0.2 setup. The optional `-PlocalDebugKey=<path>` build property selects a local debug signing file; it is not included in source delivery. Existing build dependencies are unchanged.
