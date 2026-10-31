package app.veil.airplay;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LogFilterTest {

    /** One request as the receiver logs it, lines in order (from the car's log, 0.2.0). */
    private static String[] request(String url) {
        return new String[] {
                "new request, connection 1, socket 139 type AirPlay",
                "httpd receiving on socket 139, connection 1",
                "\nhttpd: current connections:",
                "connection 0 type 1 socket 100  conn 0x7241cfa240 RAOP",
                "connection 1 type 2 socket 139  conn 0x7241cfbb00 AirPlay ACTIVE CONNECTION",
                " ",
                "httpd request received on socket 139, connection 1, method = POST, url = " + url
                        + ", protocol = HTTP/1.1",
                "conn_request",
                "\nPOST " + url + " HTTP/1.1",
                "Content-Type: application/x-apple-binary-plist\nContent-Length: 0",
                "Handling request POST with URL " + url,
                "HTTP/1.1 200 OK\nServer: AirTunes/220.68",
        };
    }

    @Test
    public void everyLineOfAPollIsLeftOut() {
        LogFilter f = new LogFilter();
        for (String url : new String[] {"/feedback", "/playback-info", "/getProperty?playbackErrorLog"}) {
            for (String line : request(url)) {
                assertTrue(url + ": " + line, f.hide(line));
            }
            assertTrue(f.hide("http_handler_get_property: ?playbackErrorLog (unhandled)"));
        }
    }

    @Test
    public void aRequestThatMattersKeepsItsAddressHeadersAndAnswer() {
        LogFilter f = new LogFilter();
        for (String line : request("/getProperty?playbackAccessLog")) {
            f.hide(line);
        }
        String[] play = request("/play");
        for (String line : play) {
            boolean plumbing = line.startsWith("new request") || line.startsWith("httpd receiving")
                    || line.contains("current connections") || line.startsWith("connection ")
                    || line.isBlank() || line.equals("conn_request") || line.startsWith("Handling");
            if (plumbing) {
                assertTrue(line, f.hide(line));
            } else {
                assertFalse(line, f.hide(line));
            }
        }
        assertFalse(f.hide("Content-Location: mlhls://localhost/abc/master.m3u8"));
    }

    @Test
    public void connectionEventsAfterAPollStillShow() {
        LogFilter f = new LogFilter();
        for (String line : request("/playback-info")) {
            f.hide(line);
        }
        assertFalse(f.hide("Connection closed on socket 139"));
        assertFalse(f.hide("master playlist slimmed: 19 -> 6 video, 4 -> 2 audio"));
    }

    @Test
    public void playlistsFromTheIphoneAreLeftOutButTheirCountsShow() {
        LogFilter f = new LogFilter();
        f.hide("httpd request received on socket 139, connection 1, method = POST, url = /action,"
                + " protocol = HTTP/1.1");
        assertTrue(f.hide("begin FCUP Response data:\n#EXTM3U\n#EXT-X-VERSION:7\nend FCUP Response data"));
        assertFalse(f.hide("\nmlhls://localhost/itag/234/mediadata.m3u8:\nreceived media playlist has   120 chunks"));
    }
}
