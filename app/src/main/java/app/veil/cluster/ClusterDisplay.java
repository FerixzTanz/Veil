package app.veil.cluster;

import java.util.List;

/**
 * Which of the head unit's screens is the instrument cluster, the panel behind the steering wheel.
 *
 * <p>On the 7X it is a 2560x536 screen that is not the centre screen (CAMCORDER's diagnostics list
 * it next to the 1440x480 HUD). It is found by size only: if no screen has that size there is no
 * cluster, and nothing guesses. Plain Java, see {@code ClusterDisplayTest}.</p>
 */
public final class ClusterDisplay {

    /** No cluster screen. */
    public static final int NONE = -1;
    public static final int DEFAULT_DISPLAY = 0;
    public static final int WIDTH = 2560;
    public static final int HEIGHT = 536;

    private ClusterDisplay() {
    }

    /** One screen: its id and real size in pixels. */
    public static final class Candidate {
        public final int id;
        public final int width;
        public final int height;

        public Candidate(int id, int width, int height) {
            this.id = id;
            this.width = width;
            this.height = height;
        }
    }

    /** The cluster's display id, or {@link #NONE}. */
    public static int find(List<Candidate> displays) {
        for (Candidate c : displays) {
            if (c.id != DEFAULT_DISPLAY && c.width == WIDTH && c.height == HEIGHT) {
                return c.id;
            }
        }
        return NONE;
    }
}
