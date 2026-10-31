package app.veil.share;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import app.veil.airplay.AirPlayLog;
import fi.iki.elonen.NanoHTTPD;

/**
 * Hands one file to a phone over the local network: the phone scans a QR code, its browser
 * opens a page with a Download button.
 *
 * <p>One file only, never a file browser. The path carries a 128-bit random token, so another
 * device on the same Wi-Fi cannot just take it, and the server runs only while the Send to
 * phone dialog is open. At most {@link #MAX_CLIENTS} connections at once.</p>
 *
 * <p>From CAMCORDER's FileShareServer (the author's app, GPL-3.0), without the photo and video
 * page. NanoHTTPD is BSD-3-Clause.</p>
 */
public class FileShareServer extends NanoHTTPD {

    /** The system picks the port: a fixed one would sooner or later clash with something. */
    private static final int AUTO_PORT = 0;
    private static final int MAX_CLIENTS = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private File file;
    private String token;
    private volatile Listener listener;

    /** What happened on the phone, for the dialog's progress line. */
    public enum Event {
        /** The phone opened the page: the QR code scanned and the network works. */
        PAGE_OPENED,
        DOWNLOAD_STARTED,
        DOWNLOAD_DONE,
        /** The download stopped before the end. */
        DOWNLOAD_STOPPED,
        /** A request that looks like the link but with the wrong token: at least the network works. */
        WRONG_LINK
    }

    /** Called on the server's threads, not the main thread. */
    public interface Listener {
        void onEvent(Event event);
    }

    public FileShareServer() {
        super(AUTO_PORT);
        setAsyncRunner(new BoundedRunner());
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    private void emit(Event event, String remote) {
        AirPlayLog.i("share: " + event + " from " + remote);
        Listener l = listener;
        if (l != null) {
            l.onEvent(event);
        }
    }

    /** One thread per connection, as NanoHTTPD's default, but capped. */
    private static final class BoundedRunner implements AsyncRunner {
        private final List<ClientHandler> running = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void closeAll() {
            for (ClientHandler handler : new ArrayList<>(running)) {
                handler.close();
            }
        }

        @Override
        public void closed(ClientHandler handler) {
            running.remove(handler);
        }

        @Override
        public void exec(ClientHandler handler) {
            if (running.size() >= MAX_CLIENTS) {
                handler.close();
                return;
            }
            running.add(handler);
            Thread thread = new Thread(handler, "share-client");
            thread.setDaemon(true);
            thread.start();
        }
    }

    /** Share this file under a new token. */
    public synchronized void share(File target) {
        file = target;
        token = randomToken();
    }

    /** The link for the QR code; null before {@link #share}. */
    public synchronized String urlFor(String host) {
        if (host == null || token == null) {
            return null;
        }
        return String.format(Locale.US, "http://%s:%d/s/%s", host, getListeningPort(), token);
    }

    private static String randomToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format(Locale.US, "%02x", b));
        }
        return sb.toString();
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri() == null ? "" : session.getUri();
        String remote = session.getRemoteIpAddress();
        File shared;
        String expected;
        synchronized (this) {
            shared = file;
            expected = token;
        }
        if (shared == null || expected == null) {
            return text(Response.Status.NOT_FOUND, "Nothing is being shared");
        }
        // HEAD asks without taking: not counted as a download
        if (session.getMethod() == Method.HEAD) {
            if (uri.equals("/f/" + expected)) {
                return newFixedLengthResponse(Response.Status.OK, mimeTypeOf(shared.getName()),
                        new ByteArrayInputStream(new byte[0]), shared.length());
            }
            return newFixedLengthResponse(uri.equals("/s/" + expected) ? Response.Status.OK
                            : Response.Status.NOT_FOUND, "text/html; charset=utf-8",
                    new ByteArrayInputStream(new byte[0]), 0);
        }
        if (uri.equals("/s/" + expected)) {
            emit(Event.PAGE_OPENED, remote);
            return downloadPage(shared, expected);
        }
        if (uri.equals("/f/" + expected)) {
            emit(Event.DOWNLOAD_STARTED, remote);
            return fileResponse(shared, remote);
        }
        // favicon and the like are not a wrong link
        if (uri.startsWith("/s/") || uri.startsWith("/f/")) {
            emit(Event.WRONG_LINK, remote);
        }
        // never say the token was wrong: that would invite guessing
        return text(Response.Status.NOT_FOUND, "Not found");
    }

    private Response fileResponse(File shared, String remote) {
        try {
            InputStream body = new Watched(new FileInputStream(shared), shared.length(),
                    done -> emit(done ? Event.DOWNLOAD_DONE : Event.DOWNLOAD_STOPPED, remote));
            Response response = newFixedLengthResponse(Response.Status.OK,
                    mimeTypeOf(shared.getName()), body, shared.length());
            response.addHeader("Content-Disposition", "attachment; filename=\"" + shared.getName() + "\"");
            return response;
        } catch (IOException e) {
            AirPlayLog.w("share: cannot read " + shared + ": " + e);
            return text(Response.Status.INTERNAL_ERROR, "Cannot read file");
        }
    }

    /**
     * No gzip: it saves little on a local network, and NanoHTTPD 2.3.1 sends a gzipped body
     * after a HEAD answer for text types, which garbles the next request on the connection
     * (found in CAMCORDER, 2026-10-11).
     */
    @Override
    protected boolean useGzipWhenAccepted(Response r) {
        return false;
    }

    /** Counts what was read: all of it is a finished download, less is a stopped one. Reports once. */
    static final class Watched extends FilterInputStream {
        interface End {
            void ended(boolean complete);
        }

        private final long length;
        private final End end;
        private long read;
        private boolean reported;

        Watched(InputStream in, long length, End end) {
            super(in);
            this.length = length;
            this.end = end;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) {
                read++;
            }
            return b;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            int n = super.read(buf, off, len);
            if (n > 0) {
                read += n;
            }
            return n;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                if (!reported) {
                    reported = true;
                    end.ended(read >= length);
                }
            }
        }
    }

    private Response downloadPage(File shared, String tokenValue) {
        String html = "<!doctype html><html lang=\"en\"><head>"
                + "<meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>" + escape(shared.getName()) + "</title><style>"
                + "body{margin:0;padding:24px 16px;background:#1a1a1a;color:#fff;"
                + "font-family:system-ui,-apple-system,sans-serif;text-align:center}"
                + ".name{margin:12px 0 4px;font-size:15px;word-break:break-all}"
                + ".size{color:#999;font-size:13px}"
                + ".btn{display:inline-block;margin-top:24px;padding:16px 32px;background:#f0622d;"
                + "color:#fff;border-radius:10px;font-size:17px;text-decoration:none}"
                + ".tip{margin-top:20px;font-size:14px;line-height:1.7;color:#ddd}"
                + "</style></head><body>"
                + "<div class=\"name\">" + escape(shared.getName()) + "</div>"
                + "<div class=\"size\">" + readableSize(shared.length()) + "</div>"
                + "<a class=\"btn\" href=\"/f/" + tokenValue + "\" download>Download</a>"
                + "<div class=\"tip\">Tap the button to save the file to your phone</div>"
                + "</body></html>";
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html);
    }

    private Response text(Response.Status status, String body) {
        return newFixedLengthResponse(status, "text/plain; charset=utf-8", body);
    }

    static String mimeTypeOf(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.US);
        if (lower.endsWith(".json")) {
            return "application/json; charset=utf-8";
        }
        if (lower.endsWith(".txt") || lower.endsWith(".log")) {
            return "text/plain; charset=utf-8";
        }
        return "application/octet-stream";
    }

    public static String readableSize(long bytes) {
        if (bytes <= 0) {
            return "—";
        }
        if (bytes < 1024L * 1024L) {
            return String.format(Locale.US, "%.0f KB", Math.max(1, bytes / 1024.0));
        }
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
