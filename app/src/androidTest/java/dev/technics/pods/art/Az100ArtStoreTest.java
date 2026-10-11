package dev.technics.pods.art;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

import dev.technics.pods.ModulePreferences;

import static org.junit.Assert.*;

/** Isolated cache tests: never overwrite the user's real artwork or device preferences. */
@RunWith(AndroidJUnit4.class)
public class Az100ArtStoreTest {
    private Context context;
    private File testDirectory;
    private String preferencesPrefix;

    @Before public void setUp() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        preferencesPrefix = "art-test-" + UUID.randomUUID();
        testDirectory = new File(target.getCacheDir(), preferencesPrefix);
        assertTrue(testDirectory.mkdirs());
        context = new ContextWrapper(target) {
            @Override public Context getApplicationContext() { return this; }
            @Override public File getFilesDir() { return testDirectory; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences(preferencesPrefix + "-" + name, mode);
            }
        };
    }

    @After public void tearDown() {
        context.getSharedPreferences("az100_art", Context.MODE_PRIVATE).edit().clear().commit();
        ModulePreferences.get(context).edit().clear().commit();
        deleteOwnedFiles(testDirectory);
    }

    @Test public void manualImportPopulatesOnlyMainAndPreservesAspectRatio() throws Exception {
        Az100ArtStore.importImage(context, writeImage("source.png", 3000, 1000, Color.GREEN));
        Az100ArtManifest manifest = Az100ArtStore.metadata(context);
        assertEquals("manual", manifest.source());
        assertEquals(java.util.Set.of("main"), manifest.slots().keySet());
        assertFalse(manifest.verified());
        assertNull(Az100ArtStore.load(context, "left"));
        assertNull(Az100ArtStore.load(context, "case"));
        Bitmap image = Az100ArtStore.load(context);
        assertNotNull(image);
        try {
            assertTrue(image.getWidth() <= Az100ArtStore.MAX_DIMENSION);
            assertTrue(image.getHeight() <= Az100ArtStore.MAX_DIMENSION);
            assertEquals(3f, image.getWidth() / (float) image.getHeight(), .02f);
            assertEquals(Color.GREEN, image.getPixel(image.getWidth() / 2, image.getHeight() / 2));
        } finally { image.recycle(); }
    }

    @Test public void failedReplacementKeepsPreviousCompleteSet() throws Exception {
        Az100ArtStore.importImage(context, writeImage("first.png", 64, 32, Color.RED));
        long version = Az100ArtStore.version(context);
        File invalid = new File(testDirectory, "invalid.bin");
        try (FileOutputStream output = new FileOutputStream(invalid)) { output.write(new byte[]{1, 2, 3}); }
        assertThrows(IOException.class, () -> Az100ArtStore.importImage(context, Uri.fromFile(invalid)));
        assertEquals(version, Az100ArtStore.version(context));
        Bitmap image = Az100ArtStore.load(context);
        assertNotNull(image);
        try { assertEquals(Color.RED, image.getPixel(32, 16)); }
        finally { image.recycle(); }
    }

    @Test public void deviceSelectionInvalidatesBoundArtworkWithoutRemovingCache() throws Exception {
        ModulePreferences.get(context).edit().putString(ModulePreferences.SELECTED_ADDRESS, "AA:BB:CC:DD:EE:01").commit();
        Az100ArtStore.importImage(context, writeImage("bound.png", 64, 64, Color.BLUE));
        ModulePreferences.get(context).edit().putString(ModulePreferences.SELECTED_ADDRESS, "AA:BB:CC:DD:EE:02").commit();
        assertNull(Az100ArtStore.load(context));
        assertEquals("AA:BB:CC:DD:EE:01", Az100ArtStore.metadata(context).deviceAddress());
        ModulePreferences.get(context).edit().putString(ModulePreferences.SELECTED_ADDRESS, "AA:BB:CC:DD:EE:01").commit();
        Bitmap image = Az100ArtStore.load(context);
        assertNotNull(image);
        image.recycle();
    }

    @Test public void clearInvalidatesAllSlotsAndFallsBackToBundledManifest() throws Exception {
        Az100ArtStore.importImage(context, writeImage("clear.png", 32, 32, Color.BLACK));
        long version = Az100ArtStore.version(context);
        Az100ArtStore.clear(context);
        assertTrue(Az100ArtStore.version(context) > version);
        Az100ArtManifest manifest = Az100ArtStore.metadata(context);
        assertEquals("bundled", manifest.source());
        assertTrue(manifest.verified());
        assertEquals(java.util.Set.of("main", "left", "right", "case"), manifest.slots().keySet());
        for (String slot : Az100ArtPolicy.SLOTS) {
            Bitmap image = Az100ArtStore.load(context, slot);
            assertNotNull(slot, image);
            image.recycle();
        }
    }

    @Test public void malformedOfficialMappingCannotReplaceManualArtwork() throws Exception {
        Az100ArtStore.importImage(context, writeImage("manual.png", 32, 32, Color.YELLOW));
        File mapping = new File(testDirectory, "mapping.xml");
        try (FileOutputStream output = new FileOutputStream(mapping)) { output.write("<bad/>".getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        Az100ArtImportResult result = Az100ArtStore.importOfficialManifest(context, Uri.fromFile(mapping));
        assertEquals(Az100ArtImportResult.Status.FAILED, result.status());
        assertEquals("manual", Az100ArtStore.metadata(context).source());
    }

    @Test public void corruptedSlotCannotBeServedAsValidArtwork() throws Exception {
        Az100ArtStore.importImage(context, writeImage("corruption.png", 32, 32, Color.WHITE));
        File[] sets = new File(new File(testDirectory, "az100_art"), "sets").listFiles();
        assertNotNull(sets);
        assertEquals(1, sets.length);
        try (FileOutputStream output = new FileOutputStream(new File(sets[0], "main.png"))) {
            output.write(new byte[]{1, 2, 3});
        }
        assertNull(Az100ArtStore.load(context));
        assertThrows(IOException.class, () -> Az100ArtStore.encodedImage(context, "main"));
    }

    private Uri writeImage(String name, int width, int height, int color) throws IOException {
        File file = new File(testDirectory, name);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try {
            bitmap.eraseColor(color);
            try (FileOutputStream output = new FileOutputStream(file)) {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IOException("Test image encode failed");
            }
        } finally { bitmap.recycle(); }
        return Uri.fromFile(file);
    }

    private static void deleteOwnedFiles(File directory) {
        File[] children = directory.listFiles();
        if (children != null) for (File child : children) {
            if (child.isDirectory()) deleteOwnedFiles(child);
            else child.delete();
        }
        directory.delete();
    }
}
