package dev.technics.pods.hook;

import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;

public class Az100OfficialSocketPolicyTest {
    @Test public void selectedAddressKeepsRenamedDeviceAndExcludesOtherNames() {
        assertTrue(Az100OfficialSocketPolicy.verified("AA:BB:CC:DD:EE:FF", "aa:bb:cc:dd:ee:ff",
                "Renamed", 1, Az100OfficialSocketPolicy.SERVICE));
        assertFalse(Az100OfficialSocketPolicy.verified("AA:BB:CC:DD:EE:FF", "AA:BB:CC:DD:EE:00",
                "EAH-AZ100", 1, Az100OfficialSocketPolicy.SERVICE));
        assertFalse(Az100OfficialSocketPolicy.verified("", "AA:BB:CC:DD:EE:FF",
                "Renamed", 1, Az100OfficialSocketPolicy.SERVICE));
        assertFalse(Az100OfficialSocketPolicy.verified("AA:BB:CC:DD:EE:FF", "AA:BB:CC:DD:EE:FF",
                "Renamed", 1, null));
    }
    @Test public void unreadableUuidOrUnverifiedChannelCannotClaimOwnership() {
        assertTrue(Az100OfficialSocketPolicy.verified("EAH-AZ100", 1, Az100OfficialSocketPolicy.SERVICE));
        assertFalse(Az100OfficialSocketPolicy.verified("EAH-AZ100", 1, null));
        assertFalse(Az100OfficialSocketPolicy.verified("EAH-AZ100", 3, Az100OfficialSocketPolicy.SERVICE));
        assertFalse(Az100OfficialSocketPolicy.verified("EAH-AZ80", 1, Az100OfficialSocketPolicy.SERVICE));
        assertFalse(Az100OfficialSocketPolicy.verified("EAH-AZ100", 1, UUID.randomUUID()));
        assertNull(Az100OfficialSocketPolicy.serviceFromFactory("createRfcommSocket", 1));
        assertEquals(Az100OfficialSocketPolicy.SERVICE, Az100OfficialSocketPolicy.serviceFromFactory(
                "createRfcommSocketToServiceRecord", Az100OfficialSocketPolicy.SERVICE));
    }

    @Test public void equalSocketObjectsHaveIndependentLifecycles() {
        Az100OfficialSocketRegistry<Object> registry = new Az100OfficialSocketRegistry<>();
        Object first = new String("equal");
        Object second = new String("equal");
        Object firstToken = new Object();
        Object secondToken = new Object();
        registry.begin(first, firstToken);
        registry.begin(second, secondToken);
        assertEquals(2, registry.snapshot().size());
        assertNotNull(registry.connected(first, firstToken));
        assertNull(registry.remove(first, secondToken));
        assertNotNull(registry.remove(first, firstToken));
        assertEquals(1, registry.snapshot().size());
        assertSame(secondToken, registry.snapshot().get(0).token());
    }

    @Test public void closeDuringConnectCannotResurrectOwnership() {
        Az100OfficialSocketRegistry<Object> registry = new Az100OfficialSocketRegistry<>();
        Object socket = new Object();
        Object token = new Object();
        registry.begin(socket, token);
        registry.remove(socket, null);
        assertNull(registry.connected(socket, token));
        assertTrue(registry.snapshot().isEmpty());
    }

    @Test public void onlyPreviouslyConnectedAndConfirmedDisconnectedSessionsArePruned() {
        Az100OfficialSocketRegistry<Object> registry = new Az100OfficialSocketRegistry<>();
        Object connecting = new Object();
        Object disconnected = new Object();
        Object unknown = new Object();
        Object disconnectedToken = new Object();
        Object unknownToken = new Object();
        registry.begin(connecting, new Object());
        registry.begin(disconnected, disconnectedToken);
        registry.connected(disconnected, disconnectedToken);
        registry.begin(unknown, unknownToken);
        registry.connected(unknown, unknownToken);
        java.util.List<Az100OfficialSocketRegistry.Session<Object>> removed =
                registry.removeDisconnected(socket -> socket == connecting || socket == disconnected);
        assertEquals(1, removed.size());
        assertSame(disconnectedToken, removed.get(0).token());
        assertEquals(2, registry.snapshot().size());
        assertNotNull(registry.get(connecting));
        assertNotNull(registry.get(unknown));
    }
}
