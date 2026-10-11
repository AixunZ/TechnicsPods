package dev.technics.pods.ipc;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import dev.technics.pods.ModulePreferences;
import dev.technics.pods.art.Az100ArtProvider;
import dev.technics.pods.state.Az100ConnectionRuntime;
import dev.technics.pods.state.Az100StateProvider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

/** Exercises provider IPC without submitting any valid Bluetooth transaction. */
@RunWith(AndroidJUnit4.class)
public class ProviderIpcTest {
    private Context context;
    private ContentResolver resolver;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        resolver = context.getContentResolver();
    }

    @Test
    public void manifestAuthoritiesResolveToExpectedExportedProvidersWithoutUriGrants() {
        assertProvider(Az100StateProvider.STATE_URI, Az100StateProvider.class.getName());
        assertProvider(Az100ArtProvider.ART_URI, Az100ArtProvider.class.getName());
    }

    @Test
    public void stateIpcContainsOwnerFreshnessAndCapabilityMetadata() {
        assumeAutomaticManagementDisabled();
        onMain(() -> {
            Bundle state = resolver.call(Az100StateProvider.STATE_URI,
                    Az100StateProvider.METHOD_STATE, null, null);

            assertNotNull(state);
            assertTrue(state.get("reachable") instanceof Boolean);
            assertTrue(state.get("updated_at") instanceof Long);
            assertTrue(state.get("generation") instanceof Long);
            assertTrue(state.get("official_busy") instanceof Boolean);
            assertTrue(state.get("connection_status") instanceof String);
            assertTrue(state.get("connection_owner") instanceof String);
            assertTrue(state.get("control_status") instanceof String);
            assertTrue(state.get("selected_address") instanceof String);
            assertTrue(state.get("start_error") instanceof String);
            assertNotNull(state.getBundle("capabilities"));
            for (String timestamp : new String[]{
                    "left_battery_updated_at", "right_battery_updated_at", "cradle_battery_updated_at",
                    "outside_mode_updated_at", "noise_level_updated_at", "ambient_level_updated_at",
                    "ambient_mode_updated_at", "controls_updated_at",
                    "left_firmware_updated_at", "right_firmware_updated_at"}) {
                assertTrue(timestamp, state.get(timestamp) instanceof Long);
            }
            assertEquals(ModulePreferences.get(context).getString(ModulePreferences.SELECTED_ADDRESS, ""),
                    state.getString("selected_address"));
        });
    }

    @Test
    public void invalidControlsAreRejectedWithoutAcquiringTransactionOrPublishingRequest() {
        assumeAutomaticManagementDisabled();
        onMain(() -> {
            Az100ConnectionRuntime runtime = Az100ConnectionRuntime.get(context);
            assumeFalse("An existing transaction must finish before this test", runtime.isRunning());
            Bundle before = state();
            for (Bundle invalid : new Bundle[]{
                    control(-2, null), control(3, null), control(-1, null),
                    control(1, 0), control(1, 101)}) {
                Bundle response = resolver.call(Az100StateProvider.STATE_URI,
                        Az100StateProvider.METHOD_CONTROL_REQUEST, null, invalid);

                assertNotNull(response);
                assertFalse(response.getBoolean("accepted"));
                assertEquals("rejected", response.getString("status"));
                assertNull(response.getString("request_id"));
                assertNotNull(response.getString("error"));
                assertEquals(before.getLong("generation"), response.getLong("generation"));
                Bundle after = state();
                assertEquals(before.getLong("generation"), after.getLong("generation"));
                assertEquals(before.getString("request_id"), after.getString("request_id"));
                assertEquals(before.getString("control_status"), after.getString("control_status"));
                assertFalse(runtime.isRunning());
            }
            assertNull(resolver.call(Az100StateProvider.STATE_URI,
                    Az100StateProvider.METHOD_CONTROL_REQUEST, null, new Bundle()));
        });
    }

    @Test
    public void artworkProviderRejectsEveryWriteModeBeforeReadingCache() throws IOException {
        for (String mode : new String[]{"w", "wt", "wa", "rw", "rwt"}) {
            assertArtworkOpenRejected(Az100ArtProvider.ART_URI, mode);
        }
    }

    @Test
    public void artworkSlotsRetainReadOnlyPngContract() {
        assertEquals("image/png", resolver.getType(Az100ArtProvider.ART_URI));
        for (String slot : new String[]{"main", "left", "right", "case"}) {
            assertEquals("image/png", resolver.getType(Az100ArtProvider.slotUri(slot)));
        }
        assertNull(resolver.getType(Uri.parse("content://dev.technics.pods.art/product?slot=main&slot=left")));
        assertNull(resolver.getType(Uri.parse("content://dev.technics.pods.art/product?slot=unknown")));
    }

    @Test
    public void artworkProviderRejectsUnexpectedPathsQueriesAndFragments() throws IOException {
        for (String suffix : new String[]{"/", "/product/extra", "/../product",
                "/product?version=1", "/product#preview"}) {
            assertArtworkOpenRejected(Uri.parse("content://dev.technics.pods.art" + suffix), "r");
        }
    }

    private void assertProvider(Uri uri, String className) {
        ProviderInfo provider = context.getPackageManager().resolveContentProvider(
                uri.getAuthority(), PackageManager.ComponentInfoFlags.of(0));

        assertNotNull(provider);
        assertEquals(context.getPackageName(), provider.packageName);
        assertEquals(className, provider.name);
        assertEquals(uri.getAuthority(), provider.authority);
        assertTrue(provider.exported);
        assertFalse(provider.grantUriPermissions);
    }

    private void assumeAutomaticManagementDisabled() {
        // Do not alter existing preferences or trigger a background read on a user's device.
        assumeFalse("Run runtime IPC cases with automatic management already disabled",
                ModulePreferences.get(context).getBoolean(ModulePreferences.AUTO_MANAGE, false));
        assumeFalse("Do not initialize recovery for a pending official session",
                ModulePreferences.get(context).getBoolean("official_session_pending", false));
    }

    private void onMain(Runnable assertions) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try { assertions.run(); }
            catch (Throwable error) { failure.set(error); }
        });
        Throwable error = failure.get();
        if (error instanceof RuntimeException runtime) throw runtime;
        if (error instanceof Error assertion) throw assertion;
        if (error != null) throw new AssertionError(error);
    }

    private Bundle state() {
        Bundle state = resolver.call(Az100StateProvider.STATE_URI,
                Az100StateProvider.METHOD_STATE, null, null);
        assertNotNull(state);
        return state;
    }

    private Bundle control(int mode, Integer noise) {
        Bundle value = new Bundle();
        value.putInt("mode", mode);
        if (noise != null) value.putInt("noise", noise);
        return value;
    }

    private void assertArtworkOpenRejected(Uri uri, String mode) throws IOException {
        try (ParcelFileDescriptor ignored = resolver.openFileDescriptor(uri, mode)) {
            fail("Artwork access must be rejected: " + uri + " mode=" + mode);
        } catch (FileNotFoundException rejected) {
            assertEquals("Unauthorized", rejected.getMessage());
        }
    }
}
