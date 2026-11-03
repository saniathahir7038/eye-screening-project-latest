# Your three photo reports

All three photos generated maps on the OnePlus 9R. They did not produce equally consistent numerical fits.

| Photo | Rings detected | Reported time | What the comparison shows |
|---|---:|---:|---|
| 1 | 10 | 14.3 seconds | Closest fit between the calculated surface and the tracked ring points. |
| 2 | 10 | 10.5 seconds | Most supported ring directions; passes the prototype fit check. |
| 3 | 8 | 19.5 seconds | Needed all four attempts. The fitted surface disagrees much more strongly with its ring points. Retry recommended. |

These notes describe the calculation, not whether an eye is healthy or has keratoconus. The reports do not independently establish that all three files depict the same eye. Attachment distance remains unmeasured.

## What changed in 0.9.1

The app now checks the fit after calculating a map. A weak fit triggers the remaining bounded retry candidates before selection. If all calculated candidates are weak, maps remain available for inspection with a permanent **LOW CONSISTENCY - RETAKE RECOMMENDED** label. Every attempt's input crop, colour crop, mask and rejection/fit details are included when you choose Share test report.

The first two saved masks pass the prototype check; the third is flagged. This is a software replay of the saved masks. The complete original photos and rejected trial masks were not included in the older reports, so the entire phone capture/inference sequence cannot be reproduced from them.

The original ring-tracking method is retained. An experimental alternative using model label IDs as ring anchors was tested locally and rejected because it lost usable coverage on these captures.

## Technical record

All three selected masks were replayed through the same current Java engine. This avoids comparing the first report's older reconstruction version directly with the two newer reports.

| Photo | Surface points | Supported directions | Fit RMS (assumed mm) | RMS / assumed radius |
|---|---:|---:|---:|---:|
| 1 | 3488 | 198/360 | 0.002887 | 0.073% |
| 2 | 4850 | 255/360 | 0.016910 | 0.469% |
| 3 | 3559 | 228/360 | 0.111546 | 2.369% |

The 1% relative-residual and 1e9 condition limits are explicit prototype engineering safeguards, not medically established thresholds. Passing means the fitted surface follows its own input points reasonably; it does not establish correct ring numbering, measured geometry, repeatability across photos, or disease accuracy. The score is not a disease probability. No classifier was trained from these three unlabelled captures.

## Test the update

Install 0.9.1 over Eye Screening without uninstalling. Select each saved photo, then open Generate maps on this phone. Older completed maps are automatically recalculated on their first visit to add the check. Share the new report, including any retry recommendation. Existing capture, imported maps, dry-eye and pterygium features are retained.
