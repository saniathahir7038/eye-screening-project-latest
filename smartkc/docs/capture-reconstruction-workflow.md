# Capture-to-reconstruction workflow (0.6.0 preview)

This change connects the combined Android app to the original desktop SmartKC reconstruction. It is an offline file exchange, not yet an on-phone port of surface reconstruction. No internet connection or server is required. The original attachment dimensions are supported; modified attachments require a separate geometry profile.

## On the phone

1. Install the updated combined Eye Screening APK. Existing ring detection remains available without calibration.
2. Open Keratoconus and choose the untouched original photograph. Cropped images remain usable for detection review only.
3. Open Camera and attachment calibration. Unknown fields can be left blank and saved as a draft. Show phone camera details lists candidate rear-camera sensor dimensions and focal lengths; this does not establish which lens, crop or zoom a particular photo used.
4. Record effective sensor width and height in the oriented photo's axes, focal length, capture digital zoom, smallest-ring-plane-to-cornea distance, and camera-pupil setback behind that plane. The sum of the last two values is the camera-to-cornea distance. These are optical geometry values; case dimensions or EXIF focal length alone do not provide a verified calibration.
5. Confirm geometry only after these values have been measured or verified for the actual lens, zoom and unchanged original attachment.
6. Export capture for reconstruction. Choose left/right eye. Confirm an untouched source only for a full original image captured with the recorded lens and zoom. Drafts and cropped images can still be exported, but the runner rejects them for reconstruction.

The package contains the exact source bytes, detection overlay, capture ID, eye, source SHA-256, detected centre in normalized source coordinates, and calibration JSON. The app preserves the source in its private storage and restores the latest capture after a restart. This is not yet a browsable patient-record database. Export only through the explicit Save ZIP action.

## On this PC

Run `scripts/reconstruct-capture.ps1` and select the exported ZIP. It uses the already-installed isolated SmartKC environment. To replace a draft with a completed calibration JSON, pass `-CalibrationProfile <path>`. Use a new output directory each time; existing output directories are not overwritten.

Equivalent command from the repository root:

```
shared/environments/smartkc/Scripts/python.exe smartkc/reconstruct_capture.py --capture capture.zip --output smartkc/artifacts/my-capture
```

Only complete measured profiles with matching image dimensions are accepted. The initial reconstruction workflow requires a full portrait 3:4 image of at least 3000x4000 pixels. Supported-resolution JPEGs are kept byte-for-byte, including EXIF orientation, to avoid changing ring edges. Higher-resolution originals are uniformly resampled to 3000x4000 while preserving the full field of view. Changed aspect ratios, unknown zoom, draft calibration and non-finite parameters are rejected. The original bytes and their hash stay unchanged in the capture package.

The original U-Net/graph localization, Arc-Step and degree-8 Zernike pipeline produces axial and tangential maps and Sim-K. For measured captures, recorded geometry replaces the study-fitted distance correction. The detected source centre bypasses the tiny central-label dependency that failed on the supplied photo. A valid mobile ring check does not guarantee that the full pipeline will find enough geometry for reconstruction.

## Back on the phone

Copy the generated result.zip to the phone. In the app, keep or restore the same source capture and use Open reconstructed maps. Maps are accepted only when the source hash AND capture ID match; study reference packages cannot be imported as live results. Archive contents and image sizes are bounded, and arbitrary archive paths are never extracted. Imported maps and the manifest are saved next to the private source record. Reopening the app restores detection of the last source; maps can be reopened from the result ZIP.

The display labels these as computer-generated research reconstructions. There is no disease classification based on imported PPK or ring irregularity. Bundled sample mode remains separately labelled.

## Reference verification

`reconstruct_capture.py --reference nokc_left --output <new-directory>` explicitly selects study reference parameters. They are never silently applied to a user capture. This reproduced Sim-K 41.21 / 40.79 D, axis 112 degrees, mean K 41.00 D, difference 0.42 D and research PPK 0.04. Reference outputs remain technical examples, not the user's results.

The measured branch was exercised with an internal engineering fixture, whose geometry was derived from a reference calculation solely to test software wiring. With the same oriented centre and original JPEG, all six reported measurements and both generated map images matched the reference exactly. It is not a physical calibration and must not be reused as a patient calibration.

## Still pending

Actual optical geometry and untouched original image from the OnePlus 9R setup; physical-device testing of the new export/import and calibration screens; reconstruction checks on repeated approved captures; on-device port and timing validation; guided CameraX capture; browsable records; and independent clinical validation. The new code does not turn the experimental ring variation threshold into a diagnostic rule.
