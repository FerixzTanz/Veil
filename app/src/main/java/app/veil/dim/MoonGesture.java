package app.veil.dim;

import app.veil.settings.Percent;

/**
 * What a finger on the moon button means. Pure arithmetic, fed raw touch
 * coordinates and timestamps by the service and tested without Android.
 *
 * <ul>
 *   <li><b>Tap</b> (finger up before it moved past the slop): toggle the veil.</li>
 *   <li><b>Drag</b> (moved past the slop before the long press fired): change the
 *       opacity. Right or down makes it darker, left or up lighter, one percent
 *       per {@code stepPx}, never below {@link #LEVEL_MIN} so the veil cannot
 *       vanish by accident.</li>
 *   <li><b>Hold then drag</b> (finger still for {@code longPressMs}, then moved):
 *       move the button. The service calls {@link #arm(long)} from a delayed
 *       runnable and shows the button has become movable.</li>
 * </ul>
 */
public final class MoonGesture {

    /** A drag never takes the veil below this. */
    public static final int LEVEL_MIN = 10;
    public static final int LEVEL_MAX = 100;

    public enum Kind { NONE, TAP, ADJUST, MOVE }

    /** What the service should do after an event. */
    public static final class Result {
        public final Kind kind;
        /** For ADJUST: the new opacity percent. */
        public final int level;
        /** For MOVE: the button's new top-left. */
        public final int x;
        public final int y;

        private Result(Kind kind, int level, int x, int y) {
            this.kind = kind;
            this.level = level;
            this.x = x;
            this.y = y;
        }

        static final Result NONE = new Result(Kind.NONE, 0, 0, 0);
    }

    private final int slopPx;
    private final int stepPx;
    private final long longPressMs;

    private boolean down;
    private float downX;
    private float downY;
    private long downTime;
    private int originLevel;
    private int originX;
    private int originY;
    private Kind mode = Kind.NONE;
    private int shownLevel = -1;

    /**
     * @param slopPx      movement below this is still a tap
     * @param stepPx      pixels of drag per percent of opacity
     * @param longPressMs how long the finger must stay still before a drag moves the button
     */
    public MoonGesture(int slopPx, int stepPx, long longPressMs) {
        this.slopPx = Math.max(1, slopPx);
        this.stepPx = Math.max(1, stepPx);
        this.longPressMs = longPressMs;
    }

    /** Finger down on the moon, with the opacity and button position at that moment. */
    public void down(float x, float y, long time, int currentLevel, int buttonX, int buttonY) {
        down = true;
        downX = x;
        downY = y;
        downTime = time;
        originLevel = Percent.clamp(currentLevel);
        originX = buttonX;
        originY = buttonY;
        mode = Kind.NONE;
        shownLevel = -1;
    }

    /**
     * The long-press timer fired. Returns true when the finger has not moved, so
     * the button is now in move mode and the service should show that.
     */
    public boolean arm(long time) {
        if (!down || mode != Kind.NONE) {
            return false;
        }
        if (time - downTime < longPressMs) {
            return false;
        }
        mode = Kind.MOVE;
        return true;
    }

    public Result move(float x, float y, long time) {
        if (!down) {
            return Result.NONE;
        }
        int dx = Math.round(x - downX);
        int dy = Math.round(y - downY);
        if (mode == Kind.NONE) {
            if (Math.abs(dx) <= slopPx && Math.abs(dy) <= slopPx) {
                return Result.NONE;
            }
            // Past the slop: a drag that started after the hold moves, before it adjusts.
            mode = time - downTime >= longPressMs ? Kind.MOVE : Kind.ADJUST;
        }
        if (mode == Kind.MOVE) {
            return new Result(Kind.MOVE, 0, originX + dx, originY + dy);
        }
        int level = clampLevel(originLevel + (dx + dy) / stepPx);
        if (level == shownLevel) {
            return Result.NONE;
        }
        shownLevel = level;
        return new Result(Kind.ADJUST, level, 0, 0);
    }

    public Result up(long time) {
        if (!down) {
            return Result.NONE;
        }
        down = false;
        Kind ended = mode;
        mode = Kind.NONE;
        if (ended == Kind.NONE) {
            return new Result(Kind.TAP, 0, 0, 0);
        }
        if (ended == Kind.MOVE) {
            // Finished a move (or a hold without moving): the service saves the position.
            return new Result(Kind.MOVE, 0, originX, originY);
        }
        return new Result(Kind.ADJUST, shownLevel < 0 ? originLevel : shownLevel, 0, 0);
    }

    public void cancel() {
        down = false;
        mode = Kind.NONE;
    }

    /** True while a hold has armed the button and the finger is still down. */
    public boolean isMoving() {
        return down && mode == Kind.MOVE;
    }

    public static int clampLevel(int level) {
        if (level < LEVEL_MIN) {
            return LEVEL_MIN;
        }
        return Math.min(level, LEVEL_MAX);
    }

    /** Keeps a button of the given size fully on a screen of the given size. */
    public static int clampPosition(int value, int size, int screen) {
        if (screen <= size) {
            return 0;
        }
        if (value < 0) {
            return 0;
        }
        return Math.min(value, screen - size);
    }
}
