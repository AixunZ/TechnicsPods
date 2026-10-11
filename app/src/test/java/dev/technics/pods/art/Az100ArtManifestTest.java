package dev.technics.pods.art;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.*;

public class Az100ArtManifestTest {
    private static final String HASH = "a".repeat(64);
    private static Az100ArtManifest parse(String xml) throws IOException {
        return Az100ArtManifest.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
    private static String official(String slots) {
        return "<az100-art schema=\"1\" model=\"EAH-AZ100\" source=\"official\" verified=\"true\" "
                + "package=\"com.panasonic.technicsaudioconnect\" color=\"black\" sourceVersionCode=\"103\" "
                + "signatureSha256=\"" + HASH + "\">" + slots + "</az100-art>";
    }
    private static String slot(String name) {
        return "<slot name=\"" + name + "\" resource=\"drawable/a1\" resourceSha256=\"" + HASH + "\" />";
    }
    @Test public void exactVersionSignatureColorAreRequiredForOfficialMapping() throws Exception {
        Az100ArtManifest manifest = parse(official(slot("main") + slot("left")));
        assertTrue(manifest.matchesOfficial(Az100ArtManifest.OFFICIAL_PACKAGE, 103, Set.of(HASH), "black"));
        assertFalse(manifest.matchesOfficial(Az100ArtManifest.OFFICIAL_PACKAGE, 104, Set.of(HASH), "black"));
        assertFalse(manifest.matchesOfficial("other.pkg", 103, Set.of(HASH), "black"));
        assertFalse(manifest.matchesOfficial(Az100ArtManifest.OFFICIAL_PACKAGE, 103, Set.of("b".repeat(64)), "black"));
        assertFalse(manifest.matchesOfficial(Az100ArtManifest.OFFICIAL_PACKAGE, 103, Set.of(HASH), "silver"));
        assertEquals(Set.of("main", "left"), manifest.slots().keySet());
        assertNull(manifest.slot("case"));
    }
    @Test public void cacheManifestRoundTripsOriginHashesAndDimensions() throws Exception {
        Az100ArtManifest input = new Az100ArtManifest("official", "black", "4.4.0", 103, HASH,
                "AA:BB:CC:DD:EE:FF", true, Map.of("main", new Az100ArtManifest.Slot("main",
                "drawable/a1", HASH, "main.png", HASH, 480, 320)));
        Az100ArtManifest output = Az100ArtManifest.read(new ByteArrayInputStream(input.encode()));
        assertEquals(input.color(), output.color());
        assertEquals(input.deviceAddress(), output.deviceAddress());
        assertEquals(input.slots(), output.slots());
        assertEquals(input.sourceVersionCode(), output.sourceVersionCode());
    }
    @Test public void placeholderCannotBeMistakenForOfficialMapping() throws Exception {
        Az100ArtManifest manifest = parse("<az100-art schema=\"1\" model=\"EAH-AZ100\" source=\"unverified\" color=\"unspecified\" />");
        assertFalse(manifest.verified());
        assertTrue(manifest.slots().isEmpty());
        assertFalse(manifest.matchesOfficial(Az100ArtManifest.OFFICIAL_PACKAGE, 103, Set.of(HASH), null));
    }
    @Test public void duplicateUnknownAndIncompleteSlotsAreRejected() {
        assertThrows(IOException.class, () -> parse(official(slot("main") + slot("main"))));
        assertThrows(IOException.class, () -> parse(official(slot("unknown"))));
        assertThrows(IOException.class, () -> parse(official("<slot name=\"main\" resource=\"drawable/a1\" />")));
        assertThrows(IOException.class, () -> parse(official(slot("main")).replace("schema=\"1\"", "schema=\"2\"")));
        assertThrows(IOException.class, () -> parse(official(slot("main")).replace("EAH-AZ100", "EAH-AZ80")));
    }
    @Test public void externalEntitiesAndOversizedManifestAreRejected() {
        assertThrows(IOException.class, () -> parse("<!DOCTYPE x [<!ENTITY secret SYSTEM 'file:///secret'>]>" + official(slot("main"))));
        assertThrows(IOException.class, () -> parse(" ".repeat(Az100ArtManifest.MAX_MANIFEST_BYTES + 1)));
    }
    @Test public void utf16CannotBypassDeclarationRejection() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-16\"?>"
                + "<!DOCTYPE x [<!ENTITY secret SYSTEM 'file:///secret'>]>" + official(slot("main"));
        assertThrows(IOException.class, () -> Az100ArtManifest.read(new ByteArrayInputStream(
                xml.getBytes(StandardCharsets.UTF_16))));
    }
    @Test public void structuredEncodingPreservesQuotedAndMultilineMetadata() throws Exception {
        Az100ArtManifest input = new Az100ArtManifest("manual", "black & silver", "4.4.0\n\"<>&", 0,
                "", "", false, Map.of("main", new Az100ArtManifest.Slot("main", "", "", "main.png", HASH, 40, 20)));
        Az100ArtManifest output = Az100ArtManifest.read(new ByteArrayInputStream(input.encode()));
        assertEquals(input.sourceVersion(), output.sourceVersion());
        assertEquals(input.color(), output.color());
    }
    @Test public void cachePathsAndDimensionOverflowAreRejected() {
        String xml = "<az100-art schema=\"1\" model=\"EAH-AZ100\" source=\"manual\" color=\"unspecified\">"
                + "<slot name=\"main\" file=\"../secret\" sha256=\"" + HASH + "\" /></az100-art>";
        assertThrows(IOException.class, () -> parse(xml));
        assertThrows(IOException.class, () -> parse(official(slot("main").replace(" />", " width=\"4294967296\" />"))));
    }
}
