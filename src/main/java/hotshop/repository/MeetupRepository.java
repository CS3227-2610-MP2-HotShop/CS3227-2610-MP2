package hotshop.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import hotshop.model.Meetup;
import hotshop.model.MeetupSlot;
import hotshop.model.MeetupStatus;
import hotshop.model.MeetupTime;
import hotshop.model.ProposalStatus;
import hotshop.model.RescheduleProposal;

/**
 * SQL mappings for offered meetup slots, meetups, and move proposals. A meetup's buyer and seller
 * are read from its sale rather than stored twice. Times are epoch milliseconds.
 */
public final class MeetupRepository {
    private static final String MEETUP_COLUMNS = "meetups.*, transactions.buyer_id, transactions.seller_id "
            + "FROM meetups JOIN transactions ON transactions.id = meetups.transaction_id";

    public void insertSlot(Connection connection, MeetupSlot slot) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO meetup_slots (id, transaction_id, start_at, "
                + "end_at, location, created_at) VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, slot.id().toString());
            statement.setString(2, slot.transactionId().toString());
            bindTime(statement, 3, slot.time());
            statement.setLong(6, slot.createdAt().toEpochMilli());
            statement.executeUpdate();
        }
    }

    public Optional<MeetupSlot> findSlot(Connection connection, UUID id) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT * FROM meetup_slots WHERE id = ?")) {
            statement.setString(1, id.toString());
            return readSlots(statement).stream().findFirst();
        }
    }

    /** Every slot offered for the sale, soonest first, including ones whose time has passed. */
    public List<MeetupSlot> findSlotsForSale(Connection connection, UUID transactionId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM meetup_slots WHERE transaction_id = ? ORDER BY start_at, id")) {
            statement.setString(1, transactionId.toString());
            return readSlots(statement);
        }
    }

    public void deleteSlot(Connection connection, UUID id) throws SQLException {
        try (var statement = connection.prepareStatement("DELETE FROM meetup_slots WHERE id = ?")) {
            statement.setString(1, id.toString());
            statement.executeUpdate();
        }
    }

    /** Removes every slot offered for the sale, once one is booked or the sale ends. */
    public void deleteSlotsForSale(Connection connection, UUID transactionId) throws SQLException {
        try (var statement = connection.prepareStatement("DELETE FROM meetup_slots WHERE transaction_id = ?")) {
            statement.setString(1, transactionId.toString());
            statement.executeUpdate();
        }
    }

    public void insertMeetup(Connection connection, Meetup meetup) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO meetups (id, transaction_id, start_at, end_at, "
                + "location, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, meetup.getId().toString());
            statement.setString(2, meetup.getTransactionId().toString());
            bindTime(statement, 3, meetup.getTime());
            statement.setString(6, meetup.getStatus().name());
            statement.setLong(7, meetup.getCreatedAt().toEpochMilli());
            statement.executeUpdate();
        }
        saveProposals(connection, meetup);
    }

    /** Saves the meetup's time and status and every move proposal. */
    public void updateMeetup(Connection connection, Meetup meetup) throws SQLException {
        try (var statement = connection.prepareStatement(
                "UPDATE meetups SET start_at = ?, end_at = ?, location = ?, status = ? WHERE id = ?")) {
            bindTime(statement, 1, meetup.getTime());
            statement.setString(4, meetup.getStatus().name());
            statement.setString(5, meetup.getId().toString());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Missing meetup during update");
            }
        }
        saveProposals(connection, meetup);
    }

    public Optional<Meetup> findMeetup(Connection connection, UUID id) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT " + MEETUP_COLUMNS + " WHERE meetups.id = ?")) {
            statement.setString(1, id.toString());
            return readMeetups(connection, statement).stream().findFirst();
        }
    }

    /** The sale's scheduled meetup, or else its most recent completed one; cancelled meetups are history only. */
    public Optional<Meetup> findCurrentForSale(Connection connection, UUID transactionId) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT " + MEETUP_COLUMNS
                + " WHERE meetups.transaction_id = ? "
                + "AND meetups.status <> 'CANCELLED' ORDER BY meetups.status = 'SCHEDULED' DESC, "
                + "meetups.created_at DESC LIMIT 1")) {
            statement.setString(1, transactionId.toString());
            return readMeetups(connection, statement).stream().findFirst();
        }
    }

    /** Scheduled meetups in which the user takes part, as buyer or seller. */
    public List<Meetup> findScheduledForParticipant(Connection connection, UUID userId) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT " + MEETUP_COLUMNS + " WHERE meetups.status = "
                + "'SCHEDULED' AND (transactions.buyer_id = ? OR transactions.seller_id = ?) "
                + "ORDER BY meetups.start_at")) {
            statement.setString(1, userId.toString());
            statement.setString(2, userId.toString());
            return readMeetups(connection, statement);
        }
    }

    private void saveProposals(Connection connection, Meetup meetup) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO meetup_reschedule_proposals (id, meetup_id, "
                + "proposer_id, start_at, end_at, location, created_at, status, resolved_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT(id) DO UPDATE SET status = excluded.status, resolved_at = excluded.resolved_at")) {
            for (RescheduleProposal proposal : meetup.getProposals()) {
                statement.setString(1, proposal.getId().toString());
                statement.setString(2, meetup.getId().toString());
                statement.setString(3, proposal.getProposerId().toString());
                bindTime(statement, 4, proposal.getTime());
                statement.setLong(7, proposal.getCreatedAt().toEpochMilli());
                statement.setString(8, proposal.getStatus().name());
                if (proposal.getResolvedAt().isPresent()) {
                    statement.setLong(9, proposal.getResolvedAt().orElseThrow().toEpochMilli());
                } else {
                    statement.setNull(9, Types.INTEGER);
                }
                statement.executeUpdate();
            }
        }
    }

    private static void bindTime(PreparedStatement statement, int first, MeetupTime time) throws SQLException {
        statement.setLong(first, time.startAt().toEpochMilli());
        statement.setLong(first + 1, time.endAt().toEpochMilli());
        statement.setString(first + 2, time.location());
    }

    private static MeetupTime readTime(ResultSet row) throws SQLException {
        return new MeetupTime(Instant.ofEpochMilli(row.getLong("start_at")),
                Instant.ofEpochMilli(row.getLong("end_at")), row.getString("location"));
    }

    private static List<MeetupSlot> readSlots(PreparedStatement statement) throws SQLException {
        List<MeetupSlot> result = new ArrayList<>();
        try (var rows = statement.executeQuery()) {
            while (rows.next()) {
                result.add(new MeetupSlot(UUID.fromString(rows.getString("id")),
                        UUID.fromString(rows.getString("transaction_id")), readTime(rows),
                        Instant.ofEpochMilli(rows.getLong("created_at"))));
            }
        }
        return result;
    }

    private List<Meetup> readMeetups(Connection connection, PreparedStatement statement) throws SQLException {
        List<Meetup> result = new ArrayList<>();
        try (var rows = statement.executeQuery()) {
            while (rows.next()) {
                UUID id = UUID.fromString(rows.getString("id"));
                result.add(Meetup.restore(new Meetup.Snapshot(id, UUID.fromString(rows.getString("transaction_id")),
                        UUID.fromString(rows.getString("buyer_id")), UUID.fromString(rows.getString("seller_id")),
                        readTime(rows), MeetupStatus.valueOf(rows.getString("status")),
                        Instant.ofEpochMilli(rows.getLong("created_at")), readProposals(connection, id))));
            }
        }
        return result;
    }

    /** Proposals in the order they were made, so the latest (possibly pending) one is last. */
    private List<RescheduleProposal> readProposals(Connection connection, UUID meetupId) throws SQLException {
        List<RescheduleProposal> result = new ArrayList<>();
        try (var statement = connection.prepareStatement("SELECT * FROM meetup_reschedule_proposals "
                + "WHERE meetup_id = ? ORDER BY created_at, rowid")) {
            statement.setString(1, meetupId.toString());
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    long resolved = rows.getLong("resolved_at");
                    Instant resolvedAt = rows.wasNull() ? null : Instant.ofEpochMilli(resolved);
                    result.add(RescheduleProposal.restore(UUID.fromString(rows.getString("id")), meetupId,
                            UUID.fromString(rows.getString("proposer_id")), readTime(rows),
                            Instant.ofEpochMilli(rows.getLong("created_at")),
                            ProposalStatus.valueOf(rows.getString("status")), resolvedAt));
                }
            }
        }
        return result;
    }
}
