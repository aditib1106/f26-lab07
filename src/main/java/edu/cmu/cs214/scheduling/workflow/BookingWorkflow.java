package edu.cmu.cs214.scheduling.workflow;

import edu.cmu.cs214.scheduling.domain.Booking;
import edu.cmu.cs214.scheduling.domain.BookingOutcome;
import edu.cmu.cs214.scheduling.domain.BookingRequest;
import edu.cmu.cs214.scheduling.domain.BookingStore;
import edu.cmu.cs214.scheduling.domain.BookingType;
import edu.cmu.cs214.scheduling.domain.Member;
import edu.cmu.cs214.scheduling.domain.Room;
import edu.cmu.cs214.scheduling.domain.TimeSlot;
import edu.cmu.cs214.scheduling.notify.NotificationHub;
import edu.cmu.cs214.scheduling.pricing.PriceCalculator;

import java.util.EnumMap;
import java.util.Map;

/**
 * The front door of the scheduler. Every booking that reaches the store goes
 * through here, and every notification the scheduler sends is published from
 * here.
 */
public class BookingWorkflow {

    static final String FACILITIES_CONTACT = "facilities@rooms.example.edu";

    private final BookingStore store;
    private final Map<BookingType, BookingTypeHandler> handlers = new EnumMap<>(BookingType.class);

    public BookingWorkflow(BookingStore store, PriceCalculator calculator, NotificationHub hub) {
        if (store == null || calculator == null || hub == null) {
            throw new IllegalArgumentException("workflow collaborators must not be null");
        }
        this.store = store;
        handlers.put(BookingType.REGULAR, new RegularHandler(store, calculator, hub));
        handlers.put(BookingType.RECURRING, new RecurringHandler(store, calculator, hub));
        handlers.put(BookingType.BLOCKED, new BlockedHandler(store, hub));
    }

    /**
     * Validates a request, writes what it can, and reports what it did.
     *
     * @return an outcome naming every booking written and every slot passed over
     */
    public BookingOutcome submit(BookingRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        Room room = store.findRoom(request.roomId());
        if (room == null) {
            return BookingOutcome.rejected("unknown room " + request.roomId());
        }

        BookingTypeHandler handler = handlers.get(request.type());
        if (handler == null) {
            return BookingOutcome.rejected("unsupported booking type " + request.type());
        }
        return handler.submit(request, room);
    }

    /**
     * Releases a booking.
     *
     * @param adminOverride set by callers acting with facilities authority
     * @return true when something was released
     */
    public boolean cancel(long bookingId, boolean adminOverride) {
        Booking booking = store.findBooking(bookingId);
        if (booking == null || booking.isCancelled()) {
            return false;
        }
        String roomName = roomNameFor(booking);

        BookingTypeHandler handler = handlers.get(booking.getType());
        if (handler == null) {
            return false;
        }
        return handler.cancel(booking, roomName, adminOverride);
    }

    /** What the holder owes for a booking, in dollars. */
    public double priceOf(long bookingId) {
        Booking booking = store.findBooking(bookingId);
        if (booking == null) {
            throw new IllegalArgumentException("unknown booking " + bookingId);
        }

        BookingTypeHandler handler = handlers.get(booking.getType());
        if (handler == null) {
            return 0.0;
        }
        return handler.priceOf(booking);
    }

    /** A one-line summary for schedules and confirmation screens. */
    public String describe(long bookingId) {
        Booking booking = store.findBooking(bookingId);
        if (booking == null) {
            return "Unknown booking #" + bookingId;
        }
        String roomName = roomNameFor(booking);

        BookingTypeHandler handler = handlers.get(booking.getType());
        if (handler == null) {
            return "Booking #" + booking.getId() + " in " + roomName;
        }
        return handler.describe(booking, roomName);
    }

    /** The rejection for an unknown member or an oversized party, or null if neither. */
    static BookingOutcome rejectMemberOrCapacity(Member member, BookingRequest request,
                                                 Room room) {
        if (member == null) {
            return BookingOutcome.rejected("unknown member " + request.memberId());
        }
        if (request.attendees() > room.getCapacity()) {
            return BookingOutcome.rejected("room " + room.getId() + " seats "
                    + room.getCapacity() + ", request wants " + request.attendees());
        }
        return null;
    }

    /** Half-open overlap: a slot that starts when another ends does not overlap it. */
    static boolean overlaps(Booking existing, TimeSlot slot) {
        return existing.getStart().compareTo(slot.end()) < 0
                && slot.start().compareTo(existing.getEnd()) < 0;
    }

    static String recipientFor(Member member) {
        return member == null ? FACILITIES_CONTACT : member.getEmail();
    }

    private String roomNameFor(Booking booking) {
        Room room = store.findRoom(booking.getRoomId());
        return room == null ? booking.getRoomId() : room.getName();
    }
}
