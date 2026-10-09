package edu.cmu.cs214.scheduling.workflow;

import edu.cmu.cs214.scheduling.domain.BookingOutcome;
import edu.cmu.cs214.scheduling.domain.BookingRequest;
import edu.cmu.cs214.scheduling.domain.BookingStore;
import edu.cmu.cs214.scheduling.domain.Member;
import edu.cmu.cs214.scheduling.domain.MembershipTier;
import edu.cmu.cs214.scheduling.domain.Room;
import edu.cmu.cs214.scheduling.notify.NotificationHub;
import edu.cmu.cs214.scheduling.pricing.PriceCalculator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization pin, written before the milestone 1 refactor.
 *
 * The RECURRING branch of submit treats touching slots as a conflict (<=),
 * while REGULAR and BLOCKED treat them as free (<). A refactor that merges the
 * overlap checks would silently change this, and no shipped test would notice.
 */
class RecurringBoundaryPinTest {

    private static final LocalDateTime MON_10AM = LocalDateTime.of(2026, 10, 5, 10, 0);
    private static final LocalDateTime MON_11AM = LocalDateTime.of(2026, 10, 5, 11, 0);
    private static final LocalDateTime NEXT_MON_9AM = LocalDateTime.of(2026, 10, 12, 9, 0);
    private static final LocalDateTime NEXT_MON_10AM = LocalDateTime.of(2026, 10, 12, 10, 0);

    private BookingWorkflow workflow;

    @BeforeEach
    void setUp() {
        BookingStore store = new BookingStore();
        store.addRoom(new Room("C-200", "Cedar Hall", 20));
        store.addMember(new Member("m-1", "Ada", "ada@rooms.example.edu", MembershipTier.BASIC));
        store.addMember(new Member("m-2", "Grace", "grace@rooms.example.edu",
                MembershipTier.PREMIER));
        workflow = new BookingWorkflow(store, new PriceCalculator(), new NotificationHub());
    }

    @Test
    void recurringSubmitSkipsAWeekThatStartsWhenAnotherBookingEnds() {
        workflow.submit(BookingRequest.regular("C-200", "m-2", NEXT_MON_9AM, NEXT_MON_10AM, 2));

        BookingOutcome outcome = workflow.submit(
                BookingRequest.recurring("C-200", "m-1", MON_10AM, MON_11AM, 2, 3));

        assertTrue(outcome.isAccepted());
        assertEquals(1, outcome.getBooked().size());
        assertEquals(1, outcome.getSkipped().size());
        assertEquals(NEXT_MON_10AM, outcome.getSkipped().get(0).start());
    }
}
