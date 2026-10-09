package dev.technics.pods.state;

import org.junit.Test;
import static org.junit.Assert.*;

public class Az100StateFreshnessTest {
    @Test public void absentFutureAndExpiredObservationsAreNotLiveState() {
        assertFalse(Az100StateFreshness.isFresh(0, 100));
        assertFalse(Az100StateFreshness.isFresh(200, 100));
        assertFalse(Az100StateFreshness.isFresh(100, 120101));
        assertTrue(Az100StateFreshness.isFresh(100, 120100));
    }
}
