package com.eyescreening.pterygium;

/** Translation-only template tracking. Never rescales or sharpens the measured signal. */
final class RingVideoTracker {
    static final int SIZE = 160;
    static final double MIN_CORRELATION = 0.65;
    static final class Frame {
        final int width, height;
        final double[] gray;
        Frame(int width, int height, double[] gray) {
            if (width < 2 || height < 2 || gray.length != width * height)
                throw new IllegalArgumentException("Invalid video frame");
            this.width = width; this.height = height; this.gray = gray;
        }
    }
    static final class Sample {
        final double[] image;
        final double x, y, correlation;
        final boolean reliable;
        Sample(double[] image, double x, double y, double correlation, boolean reliable) {
            this.image = image; this.x = x; this.y = y;
            this.correlation = correlation; this.reliable = reliable;
        }
    }
    private final double[] reference;
    private final double side;
    private double x, y;

    RingVideoTracker(Frame anchor, double cx, double cy, double side) {
        if (!Double.isFinite(cx) || !Double.isFinite(cy) || !Double.isFinite(side)
                || side < 16 || !inside(anchor, cx, cy, side))
            throw new IllegalArgumentException("Ring crop is outside the video");
        this.side = side; x = cx; y = cy;
        reference = patch(anchor, cx, cy, side);
    }

    Sample track(Frame frame) {
        double unit = side / SIZE;
        double best = -2; int bestX = 0, bestY = 0;
        // Sample the search region once. Exhaustive one-pixel translation search
        // avoids skipping narrow ring peaks between coarse grid positions.
        double[] area = square(frame, x, y, side * 224 / SIZE, 224);
        double sum = 0, variance = 0; int count = 0;
        int[] offsets = new int[800]; double[] values = new double[800];
        for (int py = 24; py < 136; py += 4) for (int px = 24; px < 136; px += 4) {
            if ((px - 80) * (px - 80) + (py - 80) * (py - 80) > 56 * 56) continue;
            offsets[count] = (py + 32) * 224 + px + 32;
            values[count] = reference[py * SIZE + px]; sum += values[count++];
        }
        for (int i = 0; i < count; i++) { values[i] -= sum / count; variance += values[i] * values[i]; }
        if (variance / count >= 4)
            for (int dy = -32; dy <= 32; dy++) for (int dx = -32; dx <= 32; dx++) {
                if (!inside(frame, x + dx * unit, y + dy * unit, side)) continue;
                double b = 0, bb = 0, ab = 0; int offset = dy * 224 + dx;
                for (int i = 0; i < count; i++) {
                    double value = area[offsets[i] + offset]; b += value; bb += value * value; ab += values[i] * value;
                }
                double vb = bb - b * b / count;
                if (vb / count < 4) continue;
                double score = ab / Math.sqrt(variance * vb);
                if (score > best) { best = score; bestX = dx; bestY = dy; }
            }
        boolean reliable = best >= MIN_CORRELATION && Math.abs(bestX) < 30 && Math.abs(bestY) < 30;
        if (reliable) { x += bestX * unit; y += bestY * unit; }
        // During a blink or a failed match, retain the last position. A failed
        // match is not a new ring position and must not supply a timing event.
        return new Sample(patch(frame, x, y, side), x, y, best, reliable);
    }

    static double[] patch(Frame frame, double cx, double cy, double side) {
        return square(frame, cx, cy, side, SIZE);
    }

    private static double[] square(Frame frame, double cx, double cy, double side, int size) {
        double[] result = new double[size * size];
        for (int py = 0; py < size; py++) for (int px = 0; px < size; px++)
            result[py * size + px] = at(frame, cx + (px + .5 - size / 2.0) * side / size,
                    cy + (py + .5 - size / 2.0) * side / size);
        return result;
    }

    private static boolean inside(Frame frame, double cx, double cy, double side) {
        return cx - side / 2 >= 0 && cy - side / 2 >= 0
                && cx + side / 2 <= frame.width - 1 && cy + side / 2 <= frame.height - 1;
    }

    private static double at(Frame frame, double x, double y) {
        x = Math.max(0, Math.min(frame.width - 1, x - .5));
        y = Math.max(0, Math.min(frame.height - 1, y - .5));
        int ix = (int) x, iy = (int) y;
        int nx = Math.min(ix + 1, frame.width - 1), ny = Math.min(iy + 1, frame.height - 1);
        double fx = x - ix, fy = y - iy;
        return (1 - fy) * ((1 - fx) * frame.gray[iy * frame.width + ix] + fx * frame.gray[iy * frame.width + nx])
                + fy * ((1 - fx) * frame.gray[ny * frame.width + ix] + fx * frame.gray[ny * frame.width + nx]);
    }
}
