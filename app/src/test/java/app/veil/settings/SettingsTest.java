package app.veil.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class SettingsTest {

    private MemoryStore store;
    private Settings settings;

    @Before
    public void setUp() {
        store = new MemoryStore();
        settings = new Settings(store);
    }

    @Test
    public void defaultsWhenNothingStored() {
        assertFalse(settings.airPlayOn());
        assertEquals("Veil", settings.receiverName());
        assertTrue(settings.castVideo());
        assertFalse(settings.dimOn());
        assertEquals(Settings.DIM_OPACITY_DEFAULT, settings.dimOpacity());
        assertEquals(Settings.DIM_BRIGHTNESS_DEFAULT, settings.dimBrightness());
        assertEquals(Settings.DIM_WARMTH_DEFAULT, settings.dimWarmth());
        assertTrue(settings.dimPassThrough());
        assertEquals(Settings.POSITION_UNSET, settings.moonX());
        assertEquals(Settings.POSITION_UNSET, settings.moonY());
    }

    @Test
    public void valuesRoundTrip() {
        settings.setAirPlayOn(true);
        settings.setCastVideo(false);
        settings.setDimOn(true);
        settings.setDimOpacity(55);
        settings.setDimBrightness(20);
        settings.setDimWarmth(35);
        settings.setDimPassThrough(false);
        settings.setMoonPosition(120, -40);

        Settings again = new Settings(store);
        assertTrue(again.airPlayOn());
        assertFalse(again.castVideo());
        assertTrue(again.dimOn());
        assertEquals(55, again.dimOpacity());
        assertEquals(20, again.dimBrightness());
        assertEquals(35, again.dimWarmth());
        assertFalse(again.dimPassThrough());
        assertEquals(120, again.moonX());
        assertEquals(-40, again.moonY());
    }

    @Test
    public void percentsAreClampedOnWriteAndRead() {
        settings.setDimOpacity(140);
        assertEquals(100, settings.dimOpacity());
        settings.setDimWarmth(-5);
        assertEquals(0, settings.dimWarmth());

        store.put(Settings.DIM_BRIGHTNESS, "250");
        assertEquals(100, settings.dimBrightness());
        store.put(Settings.DIM_BRIGHTNESS, "-1");
        assertEquals(0, settings.dimBrightness());
    }

    @Test
    public void garbageInStoreFallsBackToDefault() {
        store.put(Settings.DIM_OPACITY, "lots");
        assertEquals(Settings.DIM_OPACITY_DEFAULT, settings.dimOpacity());
        store.put(Settings.AIRPLAY_ON, "yes");
        assertFalse(settings.airPlayOn());
    }

    @Test
    public void receiverNameIsCleanedAndNeverBlank() {
        settings.setReceiverName("  My   Zeekr  ");
        assertEquals("My Zeekr", settings.receiverName());

        settings.setReceiverName("   ");
        assertEquals("Veil", settings.receiverName());
        assertEquals(null, store.get(Settings.AIRPLAY_NAME));

        settings.setReceiverName(null);
        assertEquals("Veil", settings.receiverName());

        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            longName.append('x');
        }
        settings.setReceiverName(longName.toString());
        assertEquals(Settings.RECEIVER_NAME_MAX, settings.receiverName().length());
    }

    @Test
    public void resetDimRestoresSlidersButKeepsOnOff() {
        settings.setDimOn(true);
        settings.setDimOpacity(10);
        settings.setDimBrightness(60);
        settings.setDimWarmth(70);
        settings.setDimPassThrough(false);

        settings.resetDim();

        assertTrue(settings.dimOn());
        assertEquals(Settings.DIM_OPACITY_DEFAULT, settings.dimOpacity());
        assertEquals(Settings.DIM_BRIGHTNESS_DEFAULT, settings.dimBrightness());
        assertEquals(Settings.DIM_WARMTH_DEFAULT, settings.dimWarmth());
        assertTrue(settings.dimPassThrough());
    }

    @Test
    public void listenersHearEveryChangedKey() {
        List<String> keys = new ArrayList<>();
        settings.addListener(keys::add);
        settings.setDimOpacity(30);
        settings.setAirPlayOn(true);
        settings.resetDim();
        assertEquals(List.of(Settings.DIM_OPACITY, Settings.AIRPLAY_ON, Settings.DIM_OPACITY), keys);
    }
}
