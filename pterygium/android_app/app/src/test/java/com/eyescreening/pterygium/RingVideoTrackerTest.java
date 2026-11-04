package com.eyescreening.pterygium;
import org.junit.Test;
import static org.junit.Assert.*;
public class RingVideoTrackerTest {
    private RingVideoTracker.Frame texture() {
        java.util.Random random = new java.util.Random(73);
        double[] pixels = new double[480 * 360];
        for (int i = 0; i < pixels.length; i++) pixels[i] = 30 + random.nextDouble() * 130;
        return new RingVideoTracker.Frame(480, 360, pixels);
    }
    private RingVideoTracker.Frame moved(RingVideoTracker.Frame frame, int dx, int dy, double gain, double offset) {
        double[] result = new double[frame.gray.length];
        for (int y = 0; y < frame.height; y++) for (int x = 0; x < frame.width; x++) {
            int sx = x - dx, sy = y - dy;
            if (sx >= 0 && sx < frame.width && sy >= 0 && sy < frame.height)
                result[y * frame.width + x] = frame.gray[sy * frame.width + sx] * gain + offset;
        }
        return new RingVideoTracker.Frame(frame.width, frame.height, result);
    }
    @Test public void stationaryTemplatePreservesPixels() {
        RingVideoTracker.Frame frame = texture();
        RingVideoTracker.Sample sample = new RingVideoTracker(frame, 200, 180, 160).track(frame);
        assertTrue(sample.reliable); assertEquals(200, sample.x, 0); assertEquals(180, sample.y, 0);
        assertArrayEquals(RingVideoTracker.patch(frame, 200, 180, 160), sample.image, 0);
    }
    @Test public void followsCumulativeMovementBeyondOldFixedWindow() {
        RingVideoTracker.Frame frame = texture();
        RingVideoTracker tracker = new RingVideoTracker(frame, 200, 180, 160);
        for (int shift = 12; shift <= 96; shift += 12) {
            RingVideoTracker.Sample sample = tracker.track(moved(frame, shift, 0, 1, 0));
            assertTrue(sample.reliable); assertEquals(200 + shift, sample.x, 0);
            assertEquals(1, sample.correlation, 1e-8);
        }
    }
    @Test public void brightnessAndGainDoNotChangeTheTrackedLocation() {
        RingVideoTracker.Frame frame = texture();
        RingVideoTracker.Sample sample = new RingVideoTracker(frame, 200, 180, 160).track(moved(frame, -8, 12, .8, 20));
        assertTrue(sample.reliable); assertEquals(192, sample.x, 0); assertEquals(192, sample.y, 0);
    }
    @Test public void oddPixelTranslationDoesNotFallBetweenSearchPositions() {
        RingVideoTracker.Frame frame = texture();
        RingVideoTracker.Sample sample = new RingVideoTracker(frame, 200, 180, 160).track(moved(frame, 7, -3, 1, 0));
        assertTrue(sample.reliable); assertEquals(207, sample.x, 0); assertEquals(177, sample.y, 0);
    }
    @Test public void blinkDoesNotUpdatePositionAndReopeningRecovers() {
        RingVideoTracker.Frame frame = texture(); RingVideoTracker tracker = new RingVideoTracker(frame, 200, 180, 160);
        double[] flat = new double[frame.gray.length]; java.util.Arrays.fill(flat, 80);
        RingVideoTracker.Sample blink = tracker.track(new RingVideoTracker.Frame(480, 360, flat));
        assertFalse(blink.reliable); assertEquals(200, blink.x, 0);
        assertTrue(tracker.track(moved(frame, 4, 0, 1, 0)).reliable);
    }
    @Test public void largeJumpIsRejected() {
        RingVideoTracker.Frame frame = texture();
        assertFalse(new RingVideoTracker(frame, 200, 180, 160).track(moved(frame, 80, 0, 1, 0)).reliable);
    }
    @Test public void strongFocusBlurIsNotAcceptedAsAMatchedRingPattern() {
        RingVideoTracker.Frame frame = texture(); double[] blurred = new double[frame.gray.length];
        for (int y = 1; y < frame.height - 1; y++) for (int x = 1; x < frame.width - 1; x++)
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++)
                blurred[y * frame.width + x] += frame.gray[(y + dy) * frame.width + x + dx] / 9;
        assertFalse(new RingVideoTracker(frame, 200, 180, 160)
                .track(new RingVideoTracker.Frame(frame.width, frame.height, blurred)).reliable);
    }
    @Test public void searchBoundaryIsRejected() {
        RingVideoTracker.Frame frame = texture();
        assertFalse(new RingVideoTracker(frame, 200, 180, 160).track(moved(frame, 32, 0, 1, 0)).reliable);
    }
    @Test(expected=IllegalArgumentException.class) public void offImageAnchorIsRejected() {
        new RingVideoTracker(texture(), 20, 180, 160);
    }
}
