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
import edu.cmu.cs214.scheduling.notify.NotificationMessage;
import edu.cmu.cs214.scheduling.pricing.PriceCalculator;

/** A single slot held by one member. */
class RegularHandler implements BookingTypeHandler {

    private final BookingStore store;
    private final PriceCalculator calculator;
    private final NotificationHub hub;

    RegularHandler(BookingStore store, PriceCalculator calculator, NotificationHub hub) {
        this.store = store;
        this.calculator = calculator;
        this.hub = hub;
    }

    @Override
    public BookingOutcome submit(BookingRequest request, Room room) {
        Member member = store.findMember(request.memberId());
        BookingOutcome invalid = BookingWorkflow.rejectMemberOrCapacity(member, request, room);
        if (invalid != null) {
            return invalid;
        }

        TimeSlot slot = request.slot();
        for (Booking existing : store.activeInRoom(room.getId())) {
            if (BookingWorkflow.overlaps(existing, slot)) {
                return BookingOutcome.rejected("room " + room.getId()
                        + " is already booked at " + slot.start());
            }
        }

        for (Booking held : store.allBookings()) {
            if (held.isCancelled() || !member.getId().equals(held.getMemberId())) {
                continue;
            }
            if (BookingWorkflow.overlaps(held, slot)) {
                return BookingOutcome.rejected(member.getId()
                        + " already holds a booking at " + slot.start());
            }
        }

        Booking booking = new Booking(store.nextBookingId(), room.getId(), member.getId(),
                slot, BookingType.REGULAR, null, 0);
        store.save(booking);
        hub.publish(new NotificationMessage(member.getEmail(), "Booking confirmed",
                "Room " + room.getName() + " from " + slot.start() + " to " + slot.end(),
                slot.start()));
        return BookingOutcome.confirmed(booking,
                "booked " + room.getId() + " for " + member.getId());
    }

    @Override
    public boolean cancel(Booking booking, String roomName, boolean adminOverride) {
        Member member = store.findMember(booking.getMemberId());
        booking.cancel();
        hub.publish(new NotificationMessage(BookingWorkflow.recipientFor(member),
                "Booking cancelled",
                "Room " + roomName + " on " + booking.getStart() + " is free again",
                booking.getStart()));
        return true;
    }

    @Override
    public double priceOf(Booking booking) {
        Member member = store.findMember(booking.getMemberId());
        return calculator.price(booking, member);
    }

    @Override
    public String describe(Booking booking, String roomName) {
        return "Regular booking #" + booking.getId() + " in " + roomName
                + " from " + booking.getStart() + " to " + booking.getEnd();
    }
}
