package app.veil.airplay;

import java.util.regex.Pattern;

/**
 * Decides which native receiver lines the Network page leaves out; the log file keeps them all.
 *
 * <p>Each HTTP request the iPhone makes logs about a dozen lines at debug level (the request,
 * the connection table, headers, the response). While a video is cast the iPhone polls
 * {@code /playback-info} and {@code /getProperty} several times a second besides the
 * {@code /feedback} keep-alive, so in 0.2.0 the page's last lines were only that, and the lines
 * saying why a cast failed had gone from memory within seconds. Left out: the httpd plumbing of
 * every request, and every line of a polling request. A request that matters keeps its
 * "request received" line with its address, its headers and body.</p>
 *
 * <p>Native lines arrive one at a time from the receiver's single httpd thread, in order. Not
 * thread-safe: {@link AirPlayLog} calls it under its lock.</p>
 */
final class LogFilter {

    /** Polled while connected or casting: their lines say nothing new. */
    private static final String[] POLLS = {"/feedback", "/playback-info", "/getProperty"};

    private static final Pattern CONNECTION_ROW = Pattern.compile("^connection \\d+ type \\d+ socket .*");

    /** Inside a polling request: its lines are left out until the next request or connection event. */
    private boolean quiet;

    /** True when the page should leave this native line out. */
    boolean hide(String message) {
        String m = message.trim();
        if (m.startsWith("new request, connection")
                || m.startsWith("Accepted ")
                || m.startsWith("Connection closed")
                || m.startsWith("client closed connection")
                || m.startsWith("Removing connection")) {
            quiet = false;
        }
        if (m.startsWith("httpd request received on socket")) {
            quiet = isPoll(m);
            return quiet;
        }
        return quiet || isPlumbing(m);
    }

    private static boolean isPoll(String requestLine) {
        int at = requestLine.indexOf("url = ");
        if (at < 0) {
            return false;
        }
        String url = requestLine.substring(at + "url = ".length());
        for (String poll : POLLS) {
            if (url.startsWith(poll + ",") || url.startsWith(poll + "?")) {
                return true;
            }
        }
        return false;
    }

    /** Logged for every request whatever it is. */
    private static boolean isPlumbing(String m) {
        return m.isEmpty()
                || m.startsWith("new request, connection")
                || m.startsWith("httpd receiving on socket")
                || m.startsWith("httpd: current connections:")
                || CONNECTION_ROW.matcher(m).matches()
                || m.equals("conn_request")
                || m.startsWith("Handling request ")
                || m.startsWith("Request not complete");
    }
}
