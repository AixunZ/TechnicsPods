package dev.technics.pods.state;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class Az100SessionCoordinatorTest {
    @Test
    public void beginRejectsWhileAWorkerOwnsTheSlot() {
        Az100SessionCoordinator coordinator = new Az100SessionCoordinator();
        long ticket = coordinator.begin();

        assertNotEquals(0L, ticket);
        assertEquals(0L, coordinator.begin());
        assertTrue(coordinator.isRunning());
        assertTrue(coordinator.isCurrent(ticket));
    }

    @Test
    public void multipleOfficialTokensKeepTheOwnerBusyUntilAllRelease() {
        Az100SessionCoordinator coordinator = new Az100SessionCoordinator();
        Object first = new Object();
        Object second = new Object();
        coordinator.officialBegin(first);
        coordinator.officialBegin(second);

        coordinator.officialRelease(first);

        assertTrue(coordinator.isOfficialBusy());
        assertEquals(0L, coordinator.begin());

        coordinator.officialRelease(second);

        assertFalse(coordinator.isOfficialBusy());
        assertNotEquals(0L, coordinator.begin());
    }

    @Test
    public void cancelledTicketCannotPublishAndWorkerMustFinishBeforeReopen() {
        Az100SessionCoordinator coordinator = new Az100SessionCoordinator();
        long ticket = coordinator.begin();

        coordinator.cancel();

        assertFalse(coordinator.isCurrent(ticket));
        assertTrue(coordinator.isRunning());
        assertEquals(0L, coordinator.begin());
        coordinator.finish(ticket);

        long reopened = coordinator.begin();
        assertNotEquals(0L, reopened);
        assertNotEquals(ticket, reopened);
        assertTrue(coordinator.isCurrent(reopened));
        assertFalse(coordinator.isCurrent(ticket));
    }

    @Test
    public void officialTakeoverInvalidatesPublicationUntilWorkerFinishes() {
        Az100SessionCoordinator coordinator = new Az100SessionCoordinator();
        Object official = new Object();
        long ticket = coordinator.begin();

        coordinator.officialBegin(official);

        assertFalse(coordinator.isCurrent(ticket));
        coordinator.officialRelease(official);
        assertFalse(coordinator.isCurrent(ticket));
        assertEquals(0L, coordinator.begin());
        coordinator.finish(ticket);
        assertNotEquals(0L, coordinator.begin());
    }

    @Test
    public void recoveringOfficialSessionRejectsWorkUntilRecoveryCompletes() {
        Az100SessionCoordinator coordinator = new Az100SessionCoordinator();
        coordinator.recoverOfficial(true);

        assertTrue(coordinator.isOfficialBusy());
        assertEquals(0L, coordinator.begin());
        coordinator.recoveryCompleted();

        assertFalse(coordinator.isOfficialBusy());
        assertNotEquals(0L, coordinator.begin());
    }

    @Test
    public void recoveryCompletedDoesNotRemoveLiveOfficialTokens() {
        Az100SessionCoordinator coordinator = new Az100SessionCoordinator();
        Object token = new Object();
        coordinator.officialBegin(token);
        coordinator.recoverOfficial(true);

        coordinator.recoveryCompleted();

        assertTrue(coordinator.isOfficialBusy());
        assertEquals(0L, coordinator.begin());
        coordinator.officialRelease(token);
        assertFalse(coordinator.isOfficialBusy());
    }

    @Test
    public void unknownTokenReleaseCannotUnlockRecoveringSession() {
        Az100SessionCoordinator coordinator = new Az100SessionCoordinator();
        coordinator.recoverOfficial(true);
        long generation = coordinator.generation();

        coordinator.officialRelease(new Object());

        assertEquals(generation, coordinator.generation());
        assertTrue(coordinator.isOfficialBusy());
        assertEquals(0L, coordinator.begin());
    }

    @Test
    public void oldFinishCannotReleaseAReplacementWorkerSlot() {
        Az100SessionCoordinator coordinator = new Az100SessionCoordinator();
        long oldTicket = coordinator.begin();
        coordinator.finish(oldTicket);
        long replacement = coordinator.begin();

        coordinator.finish(oldTicket);

        assertTrue(coordinator.isCurrent(replacement));
        assertEquals(0L, coordinator.begin());
    }
}
