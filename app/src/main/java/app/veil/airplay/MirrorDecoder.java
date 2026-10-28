package app.veil.airplay;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.view.Surface;


/**
 * Decodes the iPhone's mirrored screen (H.264 from UxPlay) straight onto the floating window's
 * surface. Frames arrive on a native thread; everything here is guarded by one lock.
 *
 * <p>Follows jqssun/android-airplay-server's VideoRenderer (GPL-3.0), cut down: one codec, no
 * EGL pipeline. When the window's surface goes away the codec stops, and it restarts on the
 * next keyframe once a surface is back.</p>
 *
 * <p>Decoded frames are shown by their own thread the moment the codec has them, as the
 * native audio decoder does. Collecting them only when the next frame came in left the picture
 * as many frames behind as the decoder holds, and an iPhone sends frames only while its screen
 * changes, so the end of every movement waited for the next touch.</p>
 */
final class MirrorDecoder {

    private static final String AVC = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final long FEED_WAIT_US = 20_000L;
    private static final long SHOW_WAIT_US = 10_000L;
    private static final int FEED_RETRIES = 10;
    private static final int FIRST_FEED_RETRIES = 50;

    private final Object lock = new Object();
    private MediaCodec codec;
    /** Shows {@link #codec}'s frames; runs exactly while the codec does. */
    private Thread shower;
    private volatile boolean showing;
    private Surface surface;
    private int width;
    private int height;
    private boolean firstQueued;

    void setSize(int w, int h) {
        synchronized (lock) {
            if (w == width && h == height) {
                return;
            }
            width = w;
            height = h;
            stopCodec();
        }
    }

    void setSurface(Surface s) {
        synchronized (lock) {
            if (surface == s) {
                return;
            }
            stopCodec();
            surface = s;
        }
    }

    void feed(byte[] data, long ntpTimeNs) {
        synchronized (lock) {
            if (surface == null || width <= 0 || height <= 0) {
                return;
            }
            if (codec == null) {
                // a stale reference frame decodes to garbage: only (re)start on a keyframe
                if (!isKeyframe(data)) {
                    return;
                }
                try {
                    startCodec();
                } catch (Exception e) {
                    AirPlayLog.w("decoder start failed: " + e);
                    stopCodec();
                    return;
                }
            }
            try {
                queue(data, ntpTimeNs);
            } catch (Exception e) {
                AirPlayLog.w("decoder error, resetting: " + e);
                stopCodec();
            }
        }
    }

    void release() {
        synchronized (lock) {
            stopCodec();
            surface = null;
        }
    }

    private void startCodec() throws Exception {
        MediaFormat format = MediaFormat.createVideoFormat(AVC, width, height);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, Math.max(width * height * 3 / 4, 1024 * 1024));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1);
        }
        MediaCodec c = MediaCodec.createDecoderByType(AVC);
        try {
            c.configure(format, surface, null, 0);
            c.start();
        } catch (Exception e) {
            // some decoders refuse low-latency; try once without it
            c.release();
            format = MediaFormat.createVideoFormat(AVC, width, height);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, Math.max(width * height * 3 / 4, 1024 * 1024));
            c = MediaCodec.createDecoderByType(AVC);
            c.configure(format, surface, null, 0);
            c.start();
        }
        codec = c;
        firstQueued = false;
        showing = true;
        final MediaCodec shown = c;
        shower = new Thread(() -> show(shown), "airplay-video-out");
        shower.start();
        AirPlayLog.i("decoder started " + width + "x" + height + " (" + c.getName() + ")");
    }

    /** Shower thread: each frame on screen as soon as it is decoded (mirroring is live). */
    private void show(MediaCodec c) {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        try {
            while (showing) {
                int idx = c.dequeueOutputBuffer(info, SHOW_WAIT_US);
                if (idx >= 0) {
                    c.releaseOutputBuffer(idx, true);
                }
            }
        } catch (IllegalStateException e) {
            // the codec broke; feed() resets it on its next error
        }
    }

    private void queue(byte[] data, long ntpTimeNs) {
        int retries = firstQueued ? FEED_RETRIES : FIRST_FEED_RETRIES;
        for (int i = 0; i < retries; i++) {
            int idx = codec.dequeueInputBuffer(FEED_WAIT_US);
            if (idx >= 0) {
                java.nio.ByteBuffer buf = codec.getInputBuffer(idx);
                if (buf == null) {
                    return;
                }
                buf.clear();
                buf.put(data);
                codec.queueInputBuffer(idx, 0, data.length, ntpTimeNs / 1000, 0);
                firstQueued = true;
                return;
            }
        }
    }

    private void stopCodec() {
        if (codec == null) {
            return;
        }
        // the shower must be off the codec before it stops
        showing = false;
        try {
            shower.join(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        shower = null;
        try {
            codec.stop();
        } catch (RuntimeException ignored) {
            // already broken
        }
        codec.release();
        codec = null;
    }

    /** IDR slice (5) or SPS (7) after an Annex-B start code. */
    static boolean isKeyframe(byte[] data) {
        for (int i = 0; i + 4 < data.length; i++) {
            if (data[i] == 0 && data[i + 1] == 0 && data[i + 2] == 0 && data[i + 3] == 1) {
                int type = data[i + 4] & 0x1F;
                if (type == 5 || type == 7) {
                    return true;
                }
            }
        }
        return false;
    }
}
