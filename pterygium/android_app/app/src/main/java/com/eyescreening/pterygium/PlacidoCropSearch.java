package com.eyescreening.pterygium;

import java.util.ArrayList;
import java.util.List;

/** Shape-preserving crop positions and zoom levels for live Placido photos. */
final class PlacidoCropSearch {
    static final class Window {
        final int left, top, side;

        Window(int left, int top, int side) {
            this.left = left;
            this.top = top;
            this.side = side;
        }

        boolean sameAs(Window other) {
            return left == other.left && top == other.top && side == other.side;
        }
    }

    static Window around(int width, int height, double cx, double cy, int requestedSide) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Empty image");
        int side = Math.max(1, Math.min(Math.min(width, height), requestedSide));
        int left = Math.max(0, Math.min(width - side, (int) Math.floor(cx - side / 2.0)));
        int top = Math.max(0, Math.min(height - side, (int) Math.floor(cy - side / 2.0)));
        return new Window(left, top, side);
    }

    static List<Window> windows(int width, int height) {
        List<Window> windows = new ArrayList<>();
        int shortSide = Math.min(width, height);
        // Preserve the original crop first; broaden only after detection fails.
        add(windows, around(width, height, width / 2.0, height / 2.0, shortSide / 3));
        for (double scale : new double[]{0.5, 0.75, 1.0}) {
            add(windows, around(width, height, width / 2.0, height / 2.0,
                    (int) Math.round(shortSide * scale)));
        }
        int side = Math.max(1, shortSide / 2);
        for (double y : new double[]{0.0, 0.5, 1.0}) {
            for (double x : new double[]{0.0, 0.5, 1.0}) {
                add(windows, around(width, height,
                        side / 2.0 + x * (width - side),
                        side / 2.0 + y * (height - side), side));
            }
        }
        return windows;
    }

    private static void add(List<Window> windows, Window candidate) {
        for (Window existing : windows) if (existing.sameAs(candidate)) return;
        windows.add(candidate);
    }
}
