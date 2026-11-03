# Handheld photo check - Eye Screening 0.7.0

## Use it now

Install eye-screening-0.7.0-handheld-photo-check.apk over the previous combined Eye Screening app. Open Keratoconus and select Take photo and check position, or Check a saved photo. In the phone camera use the main rear lens at 2x and tap to focus on the reflected rings. After capture, the app displays a framing overview, a ring overlay, and either Rings recognised - photo checks passed or adjustment guidance. Take another photo is beside the result. Calibration and map controls are in an optional collapsed section.

This is feedback AFTER a photo is taken, not a live camera-preview positioning system. Calibration is not required to recognise rings or perform these photo checks. Permanent mounting and optical calibration can be completed later.

## Meaning of the output

The engineering checks use at least eight recognised ring labels with the existing angular-coverage rule; a ring centre within the middle 40 percent of the image in each axis; local ring-edge detail and intensity contrast; and excessive bright clipping. The full-photo white rectangle is this framing target, not a measured cone-alignment guide. The coloured marker shows the detected reflection centre. A successful check means the photographed reflection met these checks. It cannot establish the exact relative pose, tilt, working distance or optical alignment of a separately handheld phone and attachment.

The edge-detail rule uses a normalized Laplacian variance of at least 0.01 and intensity standard deviation of at least 12 in a local circular region. Bright clipping must occupy less than 15 percent of that region. These are initial engineering heuristics tested on the supplied photos and controlled examples, not validated universal acquisition or clinical thresholds. No calibration values, corneal maps or Sim-K were invented for this mode. The main result no longer uses ring variation to label the hold or the eye.

## Changes

- Added clear ring, framing, edge-detail and contrast/clipping feedback.
- Added a full-frame overview with a target and detected centre, alongside the ring-detail overlay.
- Re-centres an already-accepted but off-centre analysis crop once when this produces an accepted crop closer to its centre. This avoids retaining a crop that unnecessarily clips part of the visible pattern.
- Added a nearby retry-photo button and made map/calibration controls optional.
- Exports photo-check details with the existing capture package for later review. Existing calibrated desktop reconstruction support remains available separately.

## Verification

34 Java unit tests passed, including seven new checks for visible rings, image direction, weak detail, bright clipping, missing centres and malformed image inputs. The debug APK compiled and its v1/v2 signatures verified using the same signer as the previous update.

The existing model was replayed on both supplied photos, with the current Java centre estimator and positioning checker applied to the exported masks/pixels. The original photo passed with 9 recognised rings; the cropped photo passed with 10 after re-centring. Both refocused analysis crops have their detected centres near the crop centre. Blurred versions of those crops failed the edge-detail check even when their clear-image ring masks were retained, isolating the new quality gate from model-detection failures.

This build has not yet been run on the physical OnePlus 9R. The computer replay approximates Android bitmap resizing; exact phone ring counts may differ. The earlier detection-only build was demonstrated on the phone by the user's screenshot, but that is not verification of all new controls. No physical alignment, repeatability or clinical accuracy was established. Source changes remain local; nothing was pushed.
