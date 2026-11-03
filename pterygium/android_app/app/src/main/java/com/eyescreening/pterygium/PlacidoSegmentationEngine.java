package com.eyescreening.pterygium;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;

import org.pytorch.IValue;
import org.pytorch.LiteModuleLoader;
import org.pytorch.Module;
import org.pytorch.Tensor;

import java.io.IOException;

final class PlacidoSegmentationEngine {
    static final int SIZE = 512;
    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] STD = {0.229f, 0.224f, 0.225f};
    private final Module module;

    static final class Result {
        final boolean accepted;
        final String failureReason;
        final Bitmap crop;
        final Bitmap overlay;
        final float centerX;
        final float centerY;
        final int detectedRings;
        final double irregularity;
        int sourceLeft, sourceTop, sourceSide;
        PositioningCheck.Result positioning;
        RingPatternPrototype.Descriptor patternDescriptor;
        Result located(PlacidoCropSearch.Window window) {
            sourceLeft=window.left; sourceTop=window.top; sourceSide=window.side;
            return this;
        }

        Result(boolean accepted, String failureReason, Bitmap crop, Bitmap overlay,
               float centerX, float centerY, int detectedRings, double irregularity) {
            this.accepted = accepted;
            this.failureReason = failureReason;
            this.crop = crop;
            this.overlay = overlay;
            this.centerX = centerX;
            this.centerY = centerY;
            this.detectedRings = detectedRings;
            this.irregularity = irregularity;
        }
    }

    PlacidoSegmentationEngine(Context context) throws IOException {
        module = LiteModuleLoader.load(AssetFiles.copyToFiles(context, "placido_segmentation.ptl"));
    }

    void close() { module.destroy(); }

    Result analyse(Bitmap source) {
        return analyseCrop(centreSquare(source));
    }

    // The saved reference samples keep their original fixed-crop path.
    Result analyseAligned(Bitmap source) {
        Result best = null;
        int refinements = 0;
        for (PlacidoCropSearch.Window window : PlacidoCropSearch.windows(
                source.getWidth(), source.getHeight())) {
            if (Thread.currentThread().isInterrupted()) break;
            Result candidate = analyseCrop(Bitmap.createBitmap(source,
                    window.left, window.top, window.side, window.side)).located(window);
            if (candidate.accepted) return recenterAccepted(source, candidate);
            if (best == null || candidate.detectedRings > best.detectedRings) best = candidate;
            // Re-centre using segmentation evidence; do not use the disease
            // score to select a crop. Uniform scaling preserves ring shape.
            if (!Float.isNaN(candidate.centerX) && !Float.isNaN(candidate.centerY)) {
                for (double scale : new double[]{1.0, 1.35, 0.75}) {
                    if (refinements >= 9) break;
                    PlacidoCropSearch.Window refined = PlacidoCropSearch.around(
                            source.getWidth(), source.getHeight(),
                            window.left + candidate.centerX * window.side / SIZE,
                            window.top + candidate.centerY * window.side / SIZE,
                            (int) Math.round(window.side * scale));
                    if (refined.sameAs(window)) continue;
                    if (Thread.currentThread().isInterrupted()) return best;
                    Result adjusted = analyseCrop(Bitmap.createBitmap(source,
                            refined.left, refined.top, refined.side, refined.side)).located(refined);
                    refinements++;
                    if (adjusted.accepted) return recenterAccepted(source, adjusted);
                    if (adjusted.detectedRings > best.detectedRings) best = adjusted;
                }
            }
        }
        return best;
    }

    private Result recenterAccepted(Bitmap source, Result candidate) {
        if(Math.hypot(candidate.centerX/SIZE-0.5,candidate.centerY/SIZE-0.5)<0.12)return candidate;
        PlacidoCropSearch.Window window=PlacidoCropSearch.around(source.getWidth(),source.getHeight(),
                candidate.sourceLeft+candidate.centerX*candidate.sourceSide/SIZE,
                candidate.sourceTop+candidate.centerY*candidate.sourceSide/SIZE,candidate.sourceSide);
        if(window.left==candidate.sourceLeft && window.top==candidate.sourceTop)return candidate;
        Result adjusted=analyseCrop(Bitmap.createBitmap(source,window.left,window.top,window.side,window.side)).located(window);
        return adjusted.accepted && Math.hypot(adjusted.centerX/SIZE-0.5,adjusted.centerY/SIZE-0.5)
                <Math.hypot(candidate.centerX/SIZE-0.5,candidate.centerY/SIZE-0.5) ? adjusted : candidate;
    }

    int[] segmentLabels(Bitmap source) {
        Bitmap scaled = Bitmap.createScaledBitmap(source, SIZE, SIZE, true);
        int[] pixels = new int[SIZE * SIZE];
        scaled.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE);
        float[] values = new float[3 * SIZE * SIZE];
        int plane = SIZE * SIZE;
        for (int i = 0; i < pixels.length; i++) {
            values[i] = (Color.red(pixels[i]) / 255f - MEAN[0]) / STD[0];
            values[plane + i] = (Color.green(pixels[i]) / 255f - MEAN[1]) / STD[1];
            values[2 * plane + i] = (Color.blue(pixels[i]) / 255f - MEAN[2]) / STD[2];
        }
        Tensor output = module.forward(IValue.from(Tensor.fromBlob(
                values, new long[]{1, 3, SIZE, SIZE}))).toTensor();
        float[] logits = output.getDataAsFloatArray();
        int[] labels = new int[plane];
        for (int i = 0; i < plane; i++) {
            int best = 0;
            float value = logits[i];
            for (int label = 1; label < 19; label++) {
                float candidate = logits[label * plane + i];
                if (candidate > value) {
                    value = candidate;
                    best = label;
                }
            }
            labels[i] = best;
        }
        return labels;
    }

    private Result analyseCrop(Bitmap crop) {
        Bitmap scaled = Bitmap.createScaledBitmap(crop, SIZE, SIZE, true);
        int[] pixels = new int[SIZE * SIZE];
        scaled.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE);
        float[] values = new float[3 * SIZE * SIZE];
        int plane = SIZE * SIZE;
        for (int i = 0; i < pixels.length; i++) {
            values[i] = (Color.red(pixels[i]) / 255f - MEAN[0]) / STD[0];
            values[plane + i] = (Color.green(pixels[i]) / 255f - MEAN[1]) / STD[1];
            values[2 * plane + i] = (Color.blue(pixels[i]) / 255f - MEAN[2]) / STD[2];
        }
        Tensor output = module.forward(IValue.from(Tensor.fromBlob(
                values, new long[]{1, 3, SIZE, SIZE}))).toTensor();
        float[] logits = output.getDataAsFloatArray();
        int[] labels = new int[plane];
        int[] counts = new int[19];
        for (int i = 0; i < plane; i++) {
            int best = 0;
            float value = logits[i];
            for (int label = 1; label < 19; label++) {
                float candidate = logits[label * plane + i];
                if (candidate > value) {
                    value = candidate;
                    best = label;
                }
            }
            labels[i] = best;
            counts[best]++;
        }

        double cx = 0, cy = 0;
        for (int i = 0; i < plane; i++) {
            if (labels[i] == 1) {
                cx += i % SIZE;
                cy += i / SIZE;
            }
        }
        boolean recoveredCenter = counts[1] < 80;
        if (recoveredCenter) {
            double[] recovered = PlacidoCenterEstimator.estimate(labels, SIZE);
            if (recovered == null) {
                return new Result(false,
                        "Centre unavailable: the central region is missing and fewer than four consistent ring shapes support a centre. Try a sharper photo with the full reflection visible.",
                        crop, renderOverlay(scaled, pixels, labels), Float.NaN, Float.NaN, 0, Double.NaN);
            }
            cx = recovered[0]; cy = recovered[1];
        } else {
            cx /= counts[1]; cy /= counts[1];
        }

        int detected = 0;
        double irregularityTotal = 0;
        int usable = 0;
        for (int label = 2; label <= 15; label++) {
            double[] sums = new double[360];
            int[] binCounts = new int[360];
            for (int i = 0; i < plane; i++) {
                if (labels[i] != label) continue;
                double dx = i % SIZE - cx;
                double dy = i / SIZE - cy;
                int angle = (int) Math.floor(Math.toDegrees(Math.atan2(dy, dx)) + 360.0) % 360;
                sums[angle] += Math.hypot(dx, dy);
                binCounts[angle]++;
            }
            int covered = 0;
            double radiusMean = 0;
            for (int angle = 0; angle < 360; angle++) {
                if (binCounts[angle] > 0) {
                    sums[angle] /= binCounts[angle];
                    radiusMean += sums[angle];
                    covered++;
                }
            }
            if (covered >= 180) {
                detected++;
                radiusMean /= covered;
                double variance = 0;
                for (int angle = 0; angle < 360; angle++) {
                    if (binCounts[angle] > 0) {
                        double delta = sums[angle] - radiusMean;
                        variance += delta * delta;
                    }
                }
                irregularityTotal += Math.sqrt(variance / covered) / radiusMean;
                usable++;
            }
        }

        Bitmap overlay = renderOverlay(scaled, pixels, labels);
        android.graphics.Canvas canvas = new android.graphics.Canvas(overlay);
        android.graphics.Paint marker = new android.graphics.Paint();
        marker.setColor(Color.WHITE);
        marker.setStrokeWidth(2f);
        canvas.drawLine((float)cx-12, (float)cy, (float)cx+12, (float)cy, marker);
        canvas.drawLine((float)cx, (float)cy-12, (float)cx, (float)cy+12, marker);
        boolean accepted = detected >= 8 && usable > 0;
        String reason = accepted ? "" : "Only " + detected + " of 14 ring labels met the coverage check; at least 8 are required. Improve focus and keep the full ring reflection visible.";
        Result result=new Result(accepted, reason, crop, overlay, (float) cx, (float) cy,
                detected, usable == 0 ? Double.NaN : irregularityTotal / usable);
        result.patternDescriptor=RingPatternPrototype.describe(labels,SIZE,cx,cy);
        return result;
    }

    private static Bitmap renderOverlay(Bitmap scaled, int[] pixels, int[] labels) {
        final int plane = SIZE * SIZE;
        Bitmap overlay = scaled.copy(Bitmap.Config.ARGB_8888, true);
        int[] overlayPixels = pixels.clone();
        int[] palette = {0x00000000, 0xFFE34F4F, 0xFF22C55E, 0xFF38BDF8,
                0xFFF59E0B, 0xFFA78BFA, 0xFFFB7185, 0xFF2DD4BF};
        for (int i = 0; i < plane; i++) {
            int label = labels[i];
            if (label > 0 && (i % 3 == 0)) {
                int tint = palette[1 + ((label - 1) % (palette.length - 1))];
                int original = pixels[i];
                overlayPixels[i] = Color.rgb(
                        (Color.red(original) + Color.red(tint)) / 2,
                        (Color.green(original) + Color.green(tint)) / 2,
                        (Color.blue(original) + Color.blue(tint)) / 2);
            }
        }
        overlay.setPixels(overlayPixels, 0, SIZE, 0, 0, SIZE, SIZE);
        return overlay;
    }

    private static Bitmap centreSquare(Bitmap source) {
        int side = Math.min(source.getWidth(), source.getHeight()) / 3;
        side = Math.max(1, side);
        int left = (source.getWidth() - side) / 2;
        int top = (source.getHeight() - side) / 2;
        return Bitmap.createBitmap(source, left, top, side, side);
    }
}
