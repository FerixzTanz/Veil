package app.veil.dim;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CentreScreenTest {

    @Test
    public void onlyDisplayZeroIsTheCentreScreen() {
        assertTrue(CentreScreen.isCentre(0));
        assertFalse(CentreScreen.isCentre(1));
        assertFalse(CentreScreen.isCentre(2));
        assertFalse(CentreScreen.isCentre(-1));
    }
}
