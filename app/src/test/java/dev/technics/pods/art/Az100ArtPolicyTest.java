package dev.technics.pods.art;

import org.junit.Test;
import static org.junit.Assert.*;

public class Az100ArtPolicyTest {
    @Test public void resourceIdentityDoesNotDependOnReadableModelFilename() {
        assertTrue(Az100ArtPolicy.resourceName("drawable/a19"));
        assertTrue(Az100ArtPolicy.resourceName("mipmap/az100_black"));
        assertFalse(Az100ArtPolicy.resourceName("assets/az100.png"));
        assertFalse(Az100ArtPolicy.resourceName("drawable/../az100"));
        assertFalse(Az100ArtPolicy.resourceName("other.pkg:drawable/a19"));
    }

    @Test public void providerAcceptsOnlyReadOnlyFixedProductUri() {
        assertTrue(Az100ArtPolicy.readableUri("content", "dev.technics.pods.art", "/product", null, null, "r"));
        assertFalse(Az100ArtPolicy.readableUri("content", "dev.technics.pods.art", "/product/other", null, null, "r"));
        assertFalse(Az100ArtPolicy.readableUri("content", "dev.technics.pods.art", "/product", "path=/etc", null, "r"));
        assertFalse(Az100ArtPolicy.readableUri("content", "dev.technics.pods.art", "/product", null, null, "rw"));
    }

    @Test public void providerAcceptsOnlyKnownSingleSlotQuery() {
        for (String slot : Az100ArtPolicy.SLOTS) {
            assertTrue(Az100ArtPolicy.readableUri("content", "dev.technics.pods.art", "/product", "slot=" + slot, null, "r"));
        }
        assertFalse(Az100ArtPolicy.readableUri("content", "dev.technics.pods.art", "/product", "slot=main&path=x", null, "r"));
        assertFalse(Az100ArtPolicy.readableUri("content", "dev.technics.pods.art", "/product", "slot=main&slot=case", null, "r"));
        assertFalse(Az100ArtPolicy.readableUri("content", "dev.technics.pods.art", "/product", "slot=%2e%2e", null, "r"));
        assertFalse(Az100ArtPolicy.readableUri("content", "dev.technics.pods.art", "/product", "slot=unknown", null, "r"));
        assertEquals("main", Az100ArtPolicy.slot(null));
    }

    @Test public void extractedFilesCannotEscapeArtworkDirectory() {
        assertTrue(Az100ArtPolicy.relativeFile("black/main.png"));
        assertFalse(Az100ArtPolicy.relativeFile("../main.png"));
        assertFalse(Az100ArtPolicy.relativeFile("black/../../secret"));
        assertFalse(Az100ArtPolicy.relativeFile("C:\\main.png"));
        assertFalse(Az100ArtPolicy.relativeFile("/main.png"));
    }
}
