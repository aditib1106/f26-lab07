package edu.cmu.cs214.scheduling.workflow;

import edu.cmu.cs214.scheduling.domain.Booking;
import edu.cmu.cs214.scheduling.domain.BookingOutcome;
import edu.cmu.cs214.scheduling.domain.BookingRequest;
import edu.cmu.cs214.scheduling.domain.Room;

/** What BookingWorkflow does for one booking type. */
interface BookingTypeHandler {

    BookingOutcome submit(BookingRequest request, Room room);

    boolean cancel(Booking booking, String roomName, boolean adminOverride);

    double priceOf(Booking booking);

    String describe(Booking booking, String roomName);
}
