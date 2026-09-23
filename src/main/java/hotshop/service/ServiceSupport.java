package hotshop.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

import hotshop.database.Database;
import hotshop.repository.UserRepository;

/** Plumbing shared by the marketplace services: time, transactions, profiles, and price text. */
final class ServiceSupport {
    private static final int CENTS_PER_DOLLAR = 100;

    private ServiceSupport() {
    }

    /** SQLite stores milliseconds, so times are truncated to match what a restart restores. */
    static Instant now(Clock clock) {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    /** Guards against the system clock moving backwards between related events. */
    static Instant latest(Instant first, Instant second) {
        return first.isAfter(second) ? first : second;
    }

    /** A status as a lowercase word for messages, for example "reserved". */
    static String describe(Enum<?> status) {
        return status.name().toLowerCase(Locale.ROOT);
    }

    /** Formats SGD cents for messages, for example 4000 as "S$40.00". */
    static String formatPrice(long cents) {
        return String.format(Locale.ROOT, "S$%,d.%02d", cents / CENTS_PER_DOLLAR, cents % CENTS_PER_DOLLAR);
    }

    /** Runs one business operation in one database transaction; storage failures hide SQL details. */
    static <T> T transaction(Database database, String failureMessage, Database.Work<T> work) {
        try {
            return database.executeTransaction(work);
        } catch (SQLException exception) {
            throw new ServiceException(ServiceException.Code.STORAGE, failureMessage, exception);
        }
    }

    /** Loads the public part of a profile that a saved record refers to. */
    static PublicProfile publicProfile(Connection connection, UserRepository users, UUID userId)
            throws SQLException {
        return PublicProfile.of(users.findById(connection, userId)
                .orElseThrow(() -> new SQLException("Referenced user is missing")));
    }
}
