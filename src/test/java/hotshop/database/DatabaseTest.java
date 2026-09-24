package hotshop.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseTest {
    private static final String FIRST = "/db/test-migration/001_first.sql";
    private static final String SECOND = "/db/test-migration/002_second.sql";
    private static final String ACCOUNTS = "/db/migration/001_accounts.sql";

    @TempDir
    Path directory;

    @Test
    void migrate_newDatabase_appliesEveryMigrationInOrder() throws Exception {
        Database database = new Database(directory.resolve("test.db"), List.of(FIRST, SECOND));
        database.migrate();
        assertEquals(List.of(1, 2), appliedVersions(database));
        assertTrue(tableExists(database, "first"));
        assertTrue(tableExists(database, "second"));
    }

    @Test
    void migrate_releasedSchema_createsListingTables() throws Exception {
        Database database = new Database(directory.resolve("test.db"));
        database.migrate();
        assertEquals(List.of(1, 2, 3, 4), appliedVersions(database));
        assertTrue(tableExists(database, "listings"));
        assertTrue(tableExists(database, "listing_images"));
    }

    @Test
    void migrate_releasedSchema_createsOfferAndTransactionTables() throws Exception {
        Database database = new Database(directory.resolve("test.db"));
        database.migrate();
        assertTrue(tableExists(database, "offers"));
        assertTrue(tableExists(database, "transactions"));
    }

    @Test
    void migrate_releasedSchema_allowsOnePendingOfferPerBuyerAndListing() throws Exception {
        Database database = seededMarketplace();
        execute(database, "INSERT INTO offers VALUES ('o1', 'l1', 'b', 100, 'WITHDRAWN', 0, 1)");
        execute(database, "INSERT INTO offers VALUES ('o2', 'l1', 'b', 100, 'PENDING', 2, NULL)");
        assertThrows(SQLException.class,
                () -> execute(database, "INSERT INTO offers VALUES ('o3', 'l1', 'b', 200, 'PENDING', 3, NULL)"));
    }

    @Test
    void migrate_releasedSchema_rejectsPendingOfferWithCloseTime() throws Exception {
        Database database = seededMarketplace();
        assertThrows(SQLException.class,
                () -> execute(database, "INSERT INTO offers VALUES ('o1', 'l1', 'b', 100, 'PENDING', 0, 1)"));
    }

    @Test
    void migrate_releasedSchema_allowsOneActiveSalePerListing() throws Exception {
        Database database = seededMarketplace();
        execute(database, "INSERT INTO offers VALUES ('o1', 'l1', 'b', 100, 'ACCEPTED', 0, 1)");
        execute(database, "INSERT INTO offers VALUES ('o2', 'l1', 'b', 100, 'ACCEPTED', 2, 3)");
        execute(database, "INSERT INTO offers VALUES ('o3', 'l1', 'b', 100, 'ACCEPTED', 4, 5)");
        execute(database, transaction("t1", "o1", "CANCELLED"));
        execute(database, transaction("t2", "o2", "ACTIVE"));
        assertThrows(SQLException.class, () -> execute(database, transaction("t3", "o3", "ACTIVE")));
    }

    @Test
    void migrate_releasedSchema_allowsOnePendingCancellationRequestPerSale() throws Exception {
        Database database = seededMarketplace();
        execute(database, "INSERT INTO offers VALUES ('o1', 'l1', 'b', 100, 'ACCEPTED', 0, 1)");
        execute(database, transaction("t1", "o1", "ACTIVE"));
        execute(database, request("r1", "WITHDRAWN", "3"));
        execute(database, request("r2", "PENDING", "NULL"));
        assertThrows(SQLException.class, () -> execute(database, request("r3", "PENDING", "NULL")));
    }

    @Test
    void migrate_releasedSchema_rejectsResolvedRequestWithoutResolutionTime() throws Exception {
        Database database = seededMarketplace();
        execute(database, "INSERT INTO offers VALUES ('o1', 'l1', 'b', 100, 'ACCEPTED', 0, 1)");
        execute(database, transaction("t1", "o1", "ACTIVE"));
        assertThrows(SQLException.class, () -> execute(database, request("r1", "REJECTED", "NULL")));
    }

    @Test
    void migrate_offersDatabaseWithSale_keepsSaleWithoutCancellationDetails() throws Exception {
        Path file = directory.resolve("test.db");
        Database offersOnly = new Database(file, List.of(ACCOUNTS, "/db/migration/002_listings.sql",
                "/db/migration/003_offers.sql"));
        offersOnly.migrate();
        execute(offersOnly, "INSERT INTO users(id, username, normalized_username, display_name) "
                + "VALUES ('u', 'alice', 'alice', 'Alice'), ('b', 'bobby', 'bobby', 'Bob')");
        insertListing(offersOnly, "l1", 100);
        execute(offersOnly, "INSERT INTO offers VALUES ('o1', 'l1', 'b', 100, 'ACCEPTED', 0, 1)");
        execute(offersOnly, transaction("t1", "o1", "ACTIVE"));
        Database upgraded = new Database(file);
        upgraded.migrate();
        String cancelledBy = upgraded.executeTransaction(connection -> {
            try (var statement = connection.createStatement();
                    var rows = statement.executeQuery("SELECT cancelled_by FROM transactions WHERE id = 't1'")) {
                rows.next();
                return rows.getString(1);
            }
        });
        assertNull(cancelledBy);
        assertTrue(tableExists(upgraded, "cancellation_requests"));
    }

    @Test
    void migrate_listingsDatabase_keepsListingsWhenAddingOffers() throws Exception {
        Path file = directory.resolve("test.db");
        Database listingsOnly = new Database(file, List.of(ACCOUNTS, "/db/migration/002_listings.sql"));
        listingsOnly.migrate();
        execute(listingsOnly, "INSERT INTO users(id, username, normalized_username, display_name) "
                + "VALUES ('u', 'alice', 'alice', 'Alice')");
        insertListing(listingsOnly, "l1", 100);
        Database upgraded = new Database(file);
        upgraded.migrate();
        assertEquals(List.of(1, 2, 3, 4), appliedVersions(upgraded));
        assertEquals(1, countRows(upgraded, "listings"));
    }

    @Test
    void migrate_accountsDatabaseWithPendingCleanup_tagsExistingRowsAsProfiles() throws Exception {
        Path file = directory.resolve("test.db");
        Database accountsOnly = new Database(file, List.of(ACCOUNTS));
        accountsOnly.migrate();
        accountsOnly.executeTransaction(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("INSERT INTO image_cleanup(filename) VALUES ('old.png')");
            }
            return null;
        });
        Database upgraded = new Database(file);
        upgraded.migrate();
        String namespace = upgraded.executeTransaction(connection -> {
            try (var statement = connection.createStatement();
                    var rows = statement.executeQuery(
                            "SELECT namespace FROM image_cleanup WHERE filename = 'old.png'")) {
                return rows.next() ? rows.getString(1) : null;
            }
        });
        assertEquals("profiles", namespace);
    }

    @Test
    void migrate_releasedSchema_rejectsPriceAboveMaximum() throws Exception {
        Database database = new Database(directory.resolve("test.db"));
        database.migrate();
        database.executeTransaction(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("INSERT INTO users(id, username, normalized_username, display_name) "
                        + "VALUES ('u', 'alice', 'alice', 'Alice')");
            }
            return null;
        });
        assertEquals(1, insertListing(database, "l1", 100_000_000));
        assertThrows(SQLException.class, () -> insertListing(database, "l2", 100_000_001));
        assertThrows(SQLException.class, () -> insertListing(database, "l3", 0));
    }

    @Test
    void migrate_olderDatabaseWithData_appliesOnlyPendingAndKeepsData() throws Exception {
        Path file = directory.resolve("test.db");
        new Database(file, List.of(FIRST)).migrate();
        Database upgraded = new Database(file, List.of(FIRST, SECOND));
        upgraded.executeTransaction(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("INSERT INTO first VALUES (42)");
            }
            return null;
        });
        upgraded.migrate();
        assertEquals(List.of(1, 2), appliedVersions(upgraded));
        assertTrue(tableExists(upgraded, "second"));
        assertEquals(1, countRows(upgraded, "first"));
    }

    @Test
    void migrate_currentDatabase_changesNothing() throws Exception {
        Database database = new Database(directory.resolve("test.db"), List.of(FIRST, SECOND));
        database.migrate();
        database.migrate();
        assertEquals(List.of(1, 2), appliedVersions(database));
    }

    @Test
    void migrate_failingMigration_keepsEarlierVersionsAndRollsBackFailedOne() throws Exception {
        Database database = new Database(directory.resolve("test.db"),
                List.of(FIRST, "/db/test-migration/002_broken.sql"));
        assertThrows(SQLException.class, database::migrate);
        assertEquals(List.of(1), appliedVersions(database));
        assertTrue(tableExists(database, "first"));
        assertFalse(tableExists(database, "partial"));
    }

    @Test
    void migrate_missingMigrationFile_throwsWithoutApplyingAny() throws Exception {
        Database database = new Database(directory.resolve("test.db"),
                List.of(FIRST, "/db/test-migration/missing.sql"));
        assertThrows(IOException.class, database::migrate);
        assertFalse(tableExists(database, "first"));
    }

    @Test
    void executeTransaction_failedWork_rollsBackAllChanges() throws Exception {
        Database database = new Database(directory.resolve("test.db"));
        database.executeTransaction(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE example (value INTEGER)");
            }
            return null;
        });
        assertThrows(SQLException.class, () -> database.executeTransaction(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("INSERT INTO example VALUES (1)");
            }
            throw new SQLException("Simulated repository failure");
        }));
        int count = database.executeTransaction(connection -> {
            try (var statement = connection.createStatement();
                    var rows = statement.executeQuery("SELECT COUNT(*) FROM example")) {
                rows.next();
                return rows.getInt(1);
            }
        });
        assertEquals(0, count);
    }

    @Test
    void executeTransaction_newConnection_enforcesForeignKeysAndBoundsLockWait() throws Exception {
        Database database = new Database(directory.resolve("test.db"));
        database.migrate();
        assertThrows(SQLException.class, () -> database.executeTransaction(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("INSERT INTO credentials "
                        + "VALUES ('missing', 'test', 1, X'01', X'01')");
            }
            return null;
        }));
        int timeout = database.executeTransaction(connection -> {
            try (var statement = connection.createStatement();
                    var rows = statement.executeQuery("PRAGMA busy_timeout")) {
                rows.next();
                return rows.getInt(1);
            }
        });
        assertEquals(5000, timeout);
    }

    private static List<Integer> appliedVersions(Database database) throws SQLException {
        return database.executeTransaction(connection -> {
            List<Integer> versions = new ArrayList<>();
            try (var statement = connection.createStatement();
                    var rows = statement.executeQuery("SELECT version FROM schema_migrations ORDER BY version")) {
                while (rows.next()) {
                    versions.add(rows.getInt(1));
                }
            }
            return versions;
        });
    }

    /** A released-schema database with seller 'u', buyer 'b', and listing 'l1'. */
    private Database seededMarketplace() throws Exception {
        Database database = new Database(directory.resolve("test.db"));
        database.migrate();
        execute(database, "INSERT INTO users(id, username, normalized_username, display_name) "
                + "VALUES ('u', 'alice', 'alice', 'Alice'), ('b', 'bobby', 'bobby', 'Bob')");
        insertListing(database, "l1", 100);
        return database;
    }

    private static String transaction(String id, String offerId, String status) {
        return "INSERT INTO transactions (id, listing_id, accepted_offer_id, buyer_id, seller_id, "
                + "agreed_price_cents, listing_title, listing_description, listing_condition, created_at, status) "
                + "VALUES ('" + id + "', 'l1', '" + offerId + "', 'b', 'u', 100, 'T', 'D', 'NEW', 0, '"
                + status + "')";
    }

    /** A request by buyer 'b' on sale 't1'; resolvedAt is SQL text such as "3" or "NULL". */
    private static String request(String id, String status, String resolvedAt) {
        return "INSERT INTO cancellation_requests (id, transaction_id, requester_id, created_at, status, "
                + "resolved_at) VALUES ('" + id + "', 't1', 'b', 2, '" + status + "', " + resolvedAt + ")";
    }

    private static void execute(Database database, String sql) throws SQLException {
        database.executeTransaction(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute(sql);
            }
            return null;
        });
    }

    private static int insertListing(Database database, String id, long price) throws SQLException {
        return database.executeTransaction(connection -> {
            try (var statement = connection.prepareStatement("INSERT INTO listings(id, seller_id, title, "
                    + "description, category, price_cents, condition, pickup_location, status, created_at, "
                    + "updated_at) VALUES (?, 'u', 'T', 'D', 'OTHER', ?, 'NEW', 'Campus', 'AVAILABLE', 0, 0)")) {
                statement.setString(1, id);
                statement.setLong(2, price);
                return statement.executeUpdate();
            }
        });
    }

    private static int countRows(Database database, String table) throws SQLException {
        return database.executeTransaction(connection -> {
            try (var statement = connection.createStatement();
                    var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                rows.next();
                return rows.getInt(1);
            }
        });
    }

    private static boolean tableExists(Database database, String table) throws SQLException {
        return database.executeTransaction(connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
                statement.setString(1, table);
                try (var rows = statement.executeQuery()) {
                    return rows.next();
                }
            }
        });
    }
}
