package dev.technics.pods.art;

import org.junit.Test;
import static org.junit.Assert.*;

public class Az100ArtPolicyTest {
    @Test public void onlyExplicitAz100RasterEntriesAreEligible() {
        assertTrue(Az100ArtPolicy.officialEntry("assets/images/eah_az100_black.png"));
        assertTrue(Az100ArtPolicy.officialEntry("res/drawable-nodpi/AZ100.webp"));
        assertFalse(Az100ArtPolicy.officialEntry("assets/images/az1000.png"));
        assertFalse(Az100ArtPolicy.officialEntry("assets/model/az80.png"));
        assertFalse(Az100ArtPolicy.officialEntry("assets/az100.xml"));
        assertFalse(Az100ArtPolicy.officialEntry("res/../az100.jpg"));
        assertFalse(Az100ArtPolicy.officialEntry("untrusted/az100.png"));
    }

    @Test public void providerAcceptsOnlyReadOnlyFixedProductUri() {
        assertTrue(Az100ArtPolicy.readableUri("content", "dev.technics.pods.art", "/product", null, null, "r"));
        assertFalse(Az100ArtPolicy.readableUri("content", "dev.technics.pods.art", "/product/other", null, null, "r"));
        assertFalse(Az100ArtPolicy.readableUri("content", "dev.technics.pods.art", "/product", "path=/etc", null, "r"));
        assertFalse(Az100ArtPolicy.readableUri("content", "dev.technics.pods.art", "/product", null, null, "rw"));
    }
}
