package hotshop.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import hotshop.model.Meetup;
import hotshop.model.MeetupSlot;
import hotshop.model.MeetupStatus;
import hotshop.repository.MeetupRepository;

/**
 * A sale's meetup as the other services see it: its summary for display, and closing it when the
 * sale completes or is cancelled. Callers supply the database transaction.
 */
final class SaleMeetups {
    private SaleMeetups() {
    }

    /** Offered slots whose time has passed are left out; they can no longer be booked. */
    static MeetupSummary load(Connection connection, MeetupRepository meetups, UUID saleId, Instant now)
            throws SQLException {
        return new MeetupSummary(saleId, futureSlots(connection, meetups, saleId, now),
                meetups.findCurrentForSale(connection, saleId));
    }

    /** The sale's offered slots that have not started yet, soonest first. */
    static List<MeetupSlot> futureSlots(Connection connection, MeetupRepository meetups, UUID saleId, Instant now)
            throws SQLException {
        return meetups.findSlotsForSale(connection, saleId).stream()
                .filter(slot -> slot.time().startAt().isAfter(now)).toList();
    }

    /** The sale completed: its scheduled meetup completes and its unbooked slots are deleted. */
    static void completeWithSale(Connection connection, MeetupRepository meetups, UUID saleId, Instant time)
            throws SQLException {
        closeWithSale(connection, meetups, saleId, time, MeetupStatus.COMPLETED);
    }

    /** The sale was cancelled: its scheduled meetup and any pending move are cancelled, and its slots deleted. */
    static void cancelWithSale(Connection connection, MeetupRepository meetups, UUID saleId, Instant time)
            throws SQLException {
        closeWithSale(connection, meetups, saleId, time, MeetupStatus.CANCELLED);
    }

    /** Scheduled meetups where the user is the seller and which have not started yet. */
    static int countUpcomingForSeller(Connection connection, MeetupRepository meetups, UUID sellerId, Instant now)
            throws SQLException {
        return (int) meetups.findScheduledForParticipant(connection, sellerId).stream()
                .filter(meetup -> meetup.getSellerId().equals(sellerId))
                .filter(meetup -> meetup.getTime().startAt().isAfter(now)).count();
    }

    private static void closeWithSale(Connection connection, MeetupRepository meetups, UUID saleId, Instant time,
            MeetupStatus outcome) throws SQLException {
        var scheduled = meetups.findCurrentForSale(connection, saleId)
                .filter(meetup -> meetup.getStatus() == MeetupStatus.SCHEDULED);
        if (scheduled.isPresent()) {
            Meetup meetup = scheduled.orElseThrow();
            Instant eventTime = ServiceSupport.latest(time, meetup.getLastEventAt());
            if (outcome == MeetupStatus.COMPLETED) {
                meetup.complete(eventTime);
            } else {
                meetup.cancel(eventTime);
            }
            meetups.updateMeetup(connection, meetup);
        }
        meetups.deleteSlotsForSale(connection, saleId);
    }
}
