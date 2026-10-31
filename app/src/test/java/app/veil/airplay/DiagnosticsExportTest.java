package app.veil.airplay;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

public class DiagnosticsExportTest {

    @Test
    public void todayAndYesterdayOldestFirstWithTheRotatedHalfBeforeTheRest() {
        Calendar c = Calendar.getInstance();
        c.set(2026, Calendar.OCTOBER, 31, 14, 2, 0);
        List<File> all = Arrays.asList(
                new File("airplay-20261031.log"),
                new File("airplay-20261029.log"),
                new File("airplay-20261030.log"),
                new File("airplay-20261031.1.log"));
        List<String> names = new ArrayList<>();
        for (File f : DiagnosticsExport.recentLogs(all, 2, c.getTime())) {
            names.add(f.getName());
        }
        assertEquals(Arrays.asList("airplay-20261030.log", "airplay-20261031.1.log", "airplay-20261031.log"), names);
    }

    @Test
    public void statesHaveNames() {
        assertEquals("casting", DiagnosticsExport.stateName(AirPlayService.STATE_CASTING));
        assertEquals("off", DiagnosticsExport.stateName(AirPlayService.STATE_NONE));
    }
}
