# Eye Screening Hardware Test APK

## Install

1. Copy the APK to an ARM64 Android phone.
2. Allow installation from the app used to open the APK.
3. Install and open **Eye Screening**.
4. Choose one of the three modules.

The app processes images and videos on the phone. It does not need an internet connection.

## Pterygium

### Live test

1. Open **Pterygium screening**.
2. Tap **Capture photo**.
3. Fill the frame with one eye while keeping the full iris and visible white of the eye in view.
4. Use bright, even light and avoid glare.
5. Take the photo and wait for the immediate result.
6. Retake the photo when the app reports poor quality or an unstable framing result.

### Sample test

1. Select one of the four labelled samples.
2. Tap **Run sample test**.
3. Confirm that pterygium samples return a suspected result and no-pterygium samples return a normal result.
4. The model score and quality measurements appear under the result.

## Keratoconus / SmartKC

### Live Placido photo and phone maps

1. Illuminate the original Placido attachment and use the OnePlus 9R main rear camera at **2x**.
2. Open **Keratoconus**, then take a photo or use **Check a saved photo**.
3. Wait for ring detection and the positioning check. Preserve the original photo.
4. Select **Generate maps on this phone**. This optional path currently requires an original portrait 3000 x 4000 image and Android 8 or later.
5. The first calculation starts automatically. Review both experimental maps and the prototype fit check.
6. A low-consistency result says **Retry recommended**. Its coloured map is not a dependable reconstruction.
7. Use **Share test report** to export the selected result and all attempted crops/masks for debugging.
8. Open **Saved photo captures** to revisit a record. Reopening its phone-map screen restores the last result. Older completed results are recalculated once to add the new check.

The maps use assumed distances. Passing the numerical fit check does not establish calibration, repeatability, disease status or clinical accuracy. Unsupported regions remain grey. The desktop capture-export and map-import controls remain under **Maps and calibration (optional)**. Imported and on-device results are stored separately.

### Sample test

1. Select one of the four included SmartKC images.
2. Tap **Run sample test**.
3. Wait while the mobile Placido model processes the image.
4. Review the saved reference PPK, aCLMI, Sim-K and maps.
5. Confirm that the **Mobile model check** reports its detected rings and irregularity.

## Dry Eye / DEDector

### Live 3D Placido test

1. Attach the 3D Placido model and switch on its light.
2. Open **Dry Eye screening** and select the eye.
3. Record a Placido video, or choose **Analyse a saved Placido video**.
4. Centre the reflected rings and hold the phone steady.
5. Ask the person to blink once, then comfortably keep the eye open.
6. Record for 12 to 15 seconds. Stop immediately if the person is uncomfortable.
7. End the recording. Analysis starts automatically.
8. Review the experimental ring-signal result or follow the inconclusive/retake reason.
9. Use **Share test video** to preserve the original video for comparison with the desktop DEDector pipeline.

The reported interval is an experimental image-signal measurement after a detected blink candidate. It is not a measured/validated NIBUT value or a disease diagnosis. An unreadable or insufficient capture returns **Inconclusive**.

### Sample test

1. Tap **Run synthetic sample**.
2. The included synthetic video tests playback, decoding and failure handling.
3. It has no patient label and must not be treated as a Dry Eye result.
4. A rejection or inconclusive result is expected.

## Result meanings

- **Prototype fit check passed:** the reconstructed surface follows its input ring points within the engineering limits; this is not a disease result.
- **Retry recommended:** the reconstruction is numerically weak; capture position or ring tracking may be contributing.
- **Pterygium pattern messages:** research model outputs; they do not provide a diagnosis.
- **Inconclusive:** the image or video could not support a reliable screening output; retake it.
- All results are research screening outputs. They are not diagnoses.

## Hardware feedback to return

For every SmartKC and Dry Eye hardware test, keep:

1. Phone model and Android version.
2. 3D attachment version.
3. Original image or video.
4. Screenshot of the app result.
5. Whether capture was easy to centre and focus.
6. Any crash, delay or repeated inconclusive result.
