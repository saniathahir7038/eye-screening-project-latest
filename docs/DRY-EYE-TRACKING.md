# Dry-eye video tracking (Android 0.9.2)

The mobile module now compensates for small translations of the reflected-ring
pattern. It can report a **Partial observation - no reliable timing result** with
the continuous usable duration and the point where tracking stopped. This is a
video-processing improvement, not a dry-eye classifier or validated NIBUT.

## Processing

The existing Placido segmentation model locates a ring crop on an anchor frame.
The existing brightness/edge close-and-reopen rule identifies a candidate blink.
The first following frame becomes the tracking reference. Using that post-blink
frame avoids requiring the initial open-eye appearance to match a keyframe from
much later in the recording.

Each 125 ms sample is decoded at up to 1280 pixels on its longest dimension. A
translation-only, normalised-correlation search follows the reference in a
bounded region around the last confident position. It uses the central circular
region of a 160-pixel patch, a one-pixel search grid within +/-32 patch pixels,
correlation >=0.65 and an interior solution (<30 pixels on each axis). Low-detail
matches are rejected. The reference is never updated onto a closed lid. Crop
scale stays fixed; no sharpening, scale correction, inpainting or interpolation
across failed samples is used to manufacture ring detail.

Brightness and edge measurements use the translated crop. The previous
post-blink baseline checks remain: adequate detail, stable edge signal, residual
registration <=6 pixels per axis and correlation >=0.65. Tracking loss ends the
continuous observation before that frame. Later recovered sections are not
joined across a failure. A short usable section is not a breakup-time estimate.
No sustained change requires at least 10 usable post-blink seconds. An observed
signal change still requires at least 500 ms of sustained reduction and passing
tracking checks through that confirmation window.

## Supplied-video replay

On 2026-11-04 the user supplied `VID_20261104_155306.mp4` (1080x1920,
approximately 15.686 seconds, 472 frames). A local CPU replay used the same Java
tracker, signal and quality classes as the Android app, with OpenCV/Pillow frame
decoding/resizing rather than Android MediaMetadataRetriever. The segmentation
model found 12 supported ring labels at the initial anchor around 5.228 seconds.

Across 126 requested samples, the replay found a candidate blink reopening at
1.500 seconds, used 1.625 seconds as the tracking reference and stopped the
continuous usable observation at the 4.000-second sample. The last accepted
sample was 3.875 seconds: 2.375 seconds after the candidate reopening. The
expected output is **Partial observation**, not a dry-eye result. This duration
describes image usability; it does not measure tear-film breakup. Different
phone decoder frames can change these exact values. The updated APK still needs
an actual phone test with this recording.

The real recording and extracted frames stay outside Git. There are no confirmed
clinical labels and no sensitivity, specificity or diagnostic-accuracy claims.

## Validation and retained functions

Java tests cover translated images (including odd-pixel and cumulative motion),
brightness changes, flat/occluded frames, search-boundary failures, blink
handling, continuous timing, interruption before event confirmation and refusal
to join sections across lost tracking. Existing SmartKC map, capture-consistency
and pterygium tests remain in the full suite. Models and the keratoconus and
pterygium implementation are unchanged by this update.

Install as an update over 0.9.1. Open Dry Eye screening, choose Analyse a saved
Placido video and select the original recording. Share a screenshot of the final
message for comparison with the desktop replay. The video is processed locally.

## Phone result discrepancy and 0.9.3 report

A subsequent phone screenshot showed a 4.00-second signal-change interval after
candidate reopening at 1.50 seconds, instead of the PC partial observation.
The screenshot alone does not establish why the outputs differ or confirm that
both runs used byte-identical video files. Motion is visible near the reported
5.50-second event in the supplied recording, but that is not a proven root cause.

Version `0.9.3-dry-eye-report` adds **Share analysis report**. Calculation and
tracking thresholds remain unchanged so the next phone run can be compared
without silently tuning a rule to this one unlabelled clip. The signal-change
heading is now **Ring-signal change - cause unconfirmed**. The video preview no
longer paints a card drawable over the video surface, and seeks to its first
frame when prepared. Actual playback and sharing still need a phone test.

The local ZIP contains `report.json`, `summary.txt`, `trace.csv`, an `anchor.png`,
8-bit aligned crop PNGs, reduced JPEG source previews and exact measured crop
records. `sample-NNNNN.bin` is a big-endian int64 requested time in milliseconds,
then 25600 IEEE754 float64 gray values in row-major order. These binary samples
preserve subpixel intensity values so the post-blink signal and baseline checks
can be independently replayed without another video decoder. Previews are lossy
and are not used to recalculate the measurement. Requested frame times are not
actual decoded presentation timestamps. The trace distinguishes frame quality
from inclusion in the reported continuous interval, including event-confirmation
frames. NaN means a check was not evaluated.

The source SHA-256, source byte length, camera-frame dimensions/rotation,
tracking-frame dimensions, app version, device and decode request are included.
No original video, local source URI or account credentials are exported. The ZIP
contains eye images and is shared only through the user's share-sheet action.
An export failure is reported separately and does not replace the analysis.
Raw staging files are removed after a completed ZIP; ZIPs remain in app cache.

For a developer replay, compile the production Java math/tracking classes and
`pterygium/android_app/tools/ReplayDryEyeReport.java`, then run the latter with the
phone ZIP as its only argument. It reads a bounded trace and fixed-size sample
records directly from the ZIP without extracting paths. The replay repeats the
signal and baseline checks using the phone's measured pixels and template flags;
it does not re-run segmentation or establish whether tracking followed the
correct physical ring pattern. The source previews and original video are still
needed to investigate that question.

## Brightness confound found in the phone report (0.9.4)

The supplied 0.9.3 phone ZIP contained 127 exact measured crops. Replaying those
pixels with method /1 reproduced its CHANGE result exactly: candidate reopening
at 1.500 seconds, change onset 5.500 seconds, interval 4.000 seconds. Therefore
video decoding is not needed to reproduce the phone result once its crop records
are available.

The post-blink baseline crop mean was 48.84195 gray units. At 5.625 seconds it
fell to 35.77093, and at 5.750 seconds to 31.93106. Edge strength fell at the same
time. Correlation still passed: normalised correlation can accept uniform gain
changes even though the raw edge magnitude decreases. This is a demonstrated
missing image-quality check, not proof of the physical cause of the darkening or
of any tear-film condition.

Method `post-blink-translation/2` rejects a sample if its mean intensity differs
from the one-second post-blink baseline mean by more than the larger of 8 gray
units or 20% of that baseline mean. These are fixed prototype engineering limits,
not medically validated thresholds. Either darkening or brightening can stop the
observation. The check applies during the baseline and through event confirmation;
no timing event can be confirmed through a failed brightness sample. The trace
now adds baseline mean, absolute mean change, brightness status and failure reason.

On the exact same phone crop records, method /2 reports PARTIAL_OBSERVATION:
brightness interruption at 5.625 seconds and last usable sample at 5.500 seconds,
4.000 seconds after candidate reopening. That 4.000 is usable observation duration,
not an image-change interval or NIBUT. The updated phone APK still needs a rerun.
Synthetic tests verify that uniform darkening can pass the old correlation check
and create a false edge event, while the new guard interrupts it. Brightening,
small stable gain changes and the existing tracking/timing cases are also checked.

The phone-reported source SHA-256 differs from the desktop copy, despite identical
byte lengths. Requested anchor times and reported durations also differ. The
reason for the file-hash discrepancy is unknown; the original PC and phone runs
must not be described as a verified byte-identical video comparison. The paired
method /1 and /2 tests above avoid this ambiguity by using the same phone pixels.

`ReplayDryEyeReport` selects method /1 or /2 from the report metadata. The optional
`--current` argument explicitly applies the current brightness guard to an older
report for a controlled comparison. The ZIP's original result is not rewritten.

### Confirmed phone retest

The subsequent 0.9.4 report `3a671faa-41d8-4b18-b3f5-d313fb9d51b0` confirmed
PARTIAL_OBSERVATION on the OnePlus LE2101, with the brightness interruption at
5.625 seconds and 4.000 seconds of usable post-blink observation. Its source
fingerprint matched the preceding phone report, and all 127 measured crop records
were byte-identical between reports. The new quality guard, rather than different
frames or tracking positions, accounts for the changed result. Independent replay
of the new ZIP matched the phone result. This verifies the specific brightness
guard and report path on this device/recording; it does not establish dry-eye
diagnostic accuracy or validate other captures.
