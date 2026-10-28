package app.veil.airplay;

/**
 * The arithmetic behind the floating window's gestures: where a drag may put it, when a push
 * or a fling docks it to a side, how a pinch resizes it around the fingers. Kept apart from
 * the touch handling so it can be tested; a wrong feel in the car is hard to describe.
 *
 * <p>From 7xDash's RearViewTouchModel (GPL-3.0), the parts the AirPlay window uses.</p>
 */
public final class WindowTouchModel {

    public enum Dock {
        NONE, LEFT, RIGHT
    }

    /** How much of the window must be pushed off screen before letting go docks it. */
    public static final float DOCK_OFFSCREEN_FRACTION = 0.5f;
    /** Glide speed when the finger was not flinging (px/s). */
    private static final float DEFAULT_GLIDE_SPEED = 2600f;

    private WindowTouchModel() {
    }

    /** Docked right now, and to which side. Read from the position: partly off screen is docked. */
    public static Dock dockedAt(int x, int width, int screenW) {
        if (width <= 0 || screenW <= 0) {
            return Dock.NONE;
        }
        if (x < 0) {
            return Dock.LEFT;
        }
        if (x + width > screenW) {
            return Dock.RIGHT;
        }
        return Dock.NONE;
    }

    /** Where an undocked window lands: fully on screen, flush with the side it hid behind. */
    public static int flushX(Dock dock, int width, int screenW) {
        return dock == Dock.RIGHT ? screenW - width : 0;
    }

    /**
     * Whether a drag on a docked window means "bring it back". A fixed, low threshold: hidden
     * things must be easy to get back, and a bigger window must not be harder to recover.
     */
    public static boolean shouldUndock(Dock dock, float dx, float velocityX,
                                       int minDistancePx, float minFlingVelocity) {
        if (dock == Dock.NONE) {
            return false;
        }
        float inward = dock == Dock.RIGHT ? -dx : dx;
        float inwardVelocity = dock == Dock.RIGHT ? -velocityX : velocityX;
        if (inward >= minDistancePx) {
            return true;
        }
        return minFlingVelocity > 0 && inwardVelocity >= minFlingVelocity;
    }

    /** Glide time: faster after a fling, a steady pace otherwise, clamped. */
    public static long glideDurationMs(int distancePx, float velocityPxPerSec, long minMs, long maxMs) {
        int distance = Math.abs(distancePx);
        if (distance == 0) {
            return minMs;
        }
        float speed = Math.abs(velocityPxPerSec);
        if (speed < 1f) {
            speed = DEFAULT_GLIDE_SPEED;
        }
        long duration = (long) (distance * 1000f / speed);
        return Math.max(minMs, Math.min(maxMs, duration));
    }

    public static final class PinchResult {
        public final int x;
        public final int y;
        public final int width;
        public final int height;

        PinchResult(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    /**
     * Pinch around the point between the fingers: that point of the window stays under the
     * fingers while the window scales, so scaling and moving are one formula. The factor is
     * clamped as a whole, so the shape is kept.
     */
    public static PinchResult pinch(int startX, int startY, int startWidth, int startHeight,
                                    float anchorRatioX, float anchorRatioY,
                                    float focusX, float focusY, float rawFactor,
                                    int minSize, int screenWidth, int screenHeight) {
        if (startWidth <= 0 || startHeight <= 0) {
            return new PinchResult(startX, startY, startWidth, startHeight);
        }
        float smallest = Math.max((float) minSize / startWidth, (float) minSize / startHeight);
        float largest = Math.min((float) Math.max(screenWidth, minSize) / startWidth,
                (float) Math.max(screenHeight, minSize) / startHeight);
        largest = Math.max(largest, smallest);
        float factor = Math.max(smallest, Math.min(largest, rawFactor));

        int width = Math.round(startWidth * factor);
        int height = Math.round(startHeight * factor);
        int x = Math.round(focusX - anchorRatioX * width);
        int y = Math.round(focusY - anchorRatioY * height);
        return new PinchResult(x, y, width, height);
    }

    /**
     * Whether letting go docks the window. A window still fully on screen never docks, however
     * fast it was flung: docking is a deliberate act. Pushed half off, or flung at a side it is
     * already touching, docks.
     */
    public static Dock deliberateDock(int windowX, int width, int screenW,
                                      float velocityX, float minFlingVelocity) {
        if (screenW <= 0 || width <= 0) {
            return Dock.NONE;
        }
        int offLeft = Math.max(0, -windowX);
        int offRight = Math.max(0, windowX + width - screenW);
        if (offLeft == 0 && offRight == 0) {
            return Dock.NONE;
        }
        float needed = width * DOCK_OFFSCREEN_FRACTION;
        if (offLeft >= needed) {
            return Dock.LEFT;
        }
        if (offRight >= needed) {
            return Dock.RIGHT;
        }
        if (minFlingVelocity > 0) {
            if (velocityX <= -minFlingVelocity && offLeft > 0) {
                return Dock.LEFT;
            }
            if (velocityX >= minFlingVelocity && offRight > 0) {
                return Dock.RIGHT;
            }
        }
        return Dock.NONE;
    }

    /** The docked position: only a strip of {@code peekWidth} stays on screen. */
    public static int dockedX(Dock dock, int windowX, int width, int screenW, int peekWidth) {
        switch (dock) {
            case LEFT:
                return peekWidth - width;
            case RIGHT:
                return screenW - peekWidth;
            default:
                return windowX;
        }
    }

    /** Keeps at least a strip on screen. A window wider than the screen can only pan. */
    public static int clampX(int x, int width, int screenW, int peekWidth) {
        if (width >= screenW) {
            return Math.max(screenW - width, Math.min(0, x));
        }
        int min = peekWidth - width;
        int max = screenW - peekWidth;
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, x));
    }

    /** No docking up or down: a window pushed off the top or bottom cannot be got back. */
    public static int clampY(int y, int height, int screenH) {
        int max = screenH - height;
        if (max < 0) {
            return 0;
        }
        return Math.max(0, Math.min(max, y));
    }
}
