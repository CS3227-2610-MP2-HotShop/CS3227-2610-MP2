package hotshop.service;

import static hotshop.service.ListingServiceTest.assertFailure;
import static hotshop.service.ListingServiceTest.draft;
import static hotshop.service.ListingServiceTest.registerAndLogin;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import hotshop.ApplicationRuntime;
import hotshop.model.ListingImage;

class ListingPhotoTest {
    @TempDir
    Path directory;

    private final TestClock clock = new TestClock(Instant.parse("2026-09-24T00:00:00Z"));

    @Test
    void createListing_newPhotos_copiesFilesInRequestedOrder() throws Exception {
        Path first = image("first.png", 1, 1);
        Path second = image("second.png", 2, 2);
        try (ApplicationRuntime runtime = open()) {
            registerAndLogin(runtime, "alice");
            var listing = runtime.getListings().createListing(draft("Chairs", 5000),
                    List.of(ListingPhoto.add(first), ListingPhoto.add(second))).join().listing();
            assertEquals(2, listing.getImages().size());
            assertEquals(savedFiles(), filenames(listing.getImages()).stream().collect(Collectors.toSet()));
            assertEquals(2, ImageIO.read(photos().resolve(listing.getImages().get(1).filename()).toFile()).getWidth());
            assertTrue(Files.exists(first));
        }
    }

    @Test
    void createListing_largestAllowedPhoto_acceptsImage() throws Exception {
        Path largest = image("largest.png", 4096, 4096);
        try (ApplicationRuntime runtime = open()) {
            registerAndLogin(runtime, "alice");
            var listing = runtime.getListings().createListing(draft("Chairs", 5000),
                    List.of(ListingPhoto.add(largest))).join().listing();
            assertEquals(1, listing.getImages().size());
        }
    }

    @Test
    void createListing_oneInvalidPhoto_savesNothingAndLeavesNoFiles() throws Exception {
        Path valid = image("valid.png", 1, 1);
        Path tooWide = image("wide.png", 4097, 1);
        try (ApplicationRuntime runtime = open()) {
            registerAndLogin(runtime, "alice");
            assertFailure(ServiceException.Code.VALIDATION, () -> runtime.getListings().createListing(
                    draft("Chairs", 5000), List.of(ListingPhoto.add(valid), ListingPhoto.add(tooWide))).join());
            assertEquals(List.of(), runtime.getListings().getMyListings().join());
            assertEquals(Set.of(), savedFiles());
        }
    }

    @Test
    void createListing_photoTallerThanLimit_reportsValidation() throws Exception {
        assertRejectedPhoto(image("tall.png", 1, 4097));
    }

    @Test
    void createListing_nonImageFile_reportsValidation() throws Exception {
        Path text = directory.resolve("notes.png");
        Files.writeString(text, "not an image");
        assertRejectedPhoto(text);
    }

    @Test
    void createListing_fileAboveTenMebibytes_reportsValidation() throws Exception {
        Path large = directory.resolve("large.png");
        Files.write(large, new byte[10 * 1024 * 1024 + 1]);
        assertRejectedPhoto(large);
    }

    private void assertRejectedPhoto(Path source) throws Exception {
        try (ApplicationRuntime runtime = open()) {
            registerAndLogin(runtime, "alice");
            assertFailure(ServiceException.Code.VALIDATION, () -> runtime.getListings().createListing(
                    draft("Chairs", 5000), List.of(ListingPhoto.add(source))).join());
            assertEquals(Set.of(), savedFiles());
        }
    }

    @Test
    void createListing_elevenPhotos_reportsValidationWithoutCopying() throws Exception {
        Path source = image("photo.png", 1, 1);
        try (ApplicationRuntime runtime = open()) {
            registerAndLogin(runtime, "alice");
            List<ListingPhoto> photos = Collections.nCopies(11, ListingPhoto.add(source));
            assertFailure(ServiceException.Code.VALIDATION,
                    () -> runtime.getListings().createListing(draft("Chairs", 5000), photos).join());
            assertEquals(Set.of(), savedFiles());
        }
    }

    @Test
    void createListing_keptPhoto_reportsValidation() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            registerAndLogin(runtime, "alice");
            assertFailure(ServiceException.Code.VALIDATION, () -> runtime.getListings().createListing(
                    draft("Chairs", 5000), List.of(ListingPhoto.keep(UUID.randomUUID() + ".png"))).join());
        }
    }

    @Test
    void updateListing_keepReorderAddAndRemove_savesOrderAndRetiresRemovedPhoto() throws Exception {
        Path source = image("photo.png", 1, 1);
        try (ApplicationRuntime runtime = open()) {
            registerAndLogin(runtime, "alice");
            var listings = runtime.getListings();
            var original = listings.createListing(draft("Chairs", 5000),
                    List.of(ListingPhoto.add(source), ListingPhoto.add(source))).join().listing();
            String first = original.getImages().get(0).filename();
            String removed = original.getImages().get(1).filename();
            clock.advanceSeconds(60);
            var updated = listings.updateListing(original.getId(), draft("Chairs", 5000),
                    List.of(ListingPhoto.add(source), ListingPhoto.keep(first))).join().listing();
            List<String> names = filenames(updated.getImages());
            assertEquals(first, names.get(1));
            assertEquals(Set.copyOf(names), savedFiles());
            assertFalse(Files.exists(photos().resolve(removed)));
            assertEquals(names, filenames(listings.getListing(original.getId()).join().listing().getImages()));
        }
    }

    @Test
    void updateListing_photoFromAnotherListing_reportsValidation() throws Exception {
        Path source = image("photo.png", 1, 1);
        try (ApplicationRuntime runtime = open()) {
            registerAndLogin(runtime, "alice");
            var listings = runtime.getListings();
            String other = listings.createListing(draft("Other", 100), List.of(ListingPhoto.add(source)))
                    .join().listing().getImages().get(0).filename();
            UUID id = listings.createListing(draft("Chairs", 5000), List.of()).join().listing().getId();
            assertFailure(ServiceException.Code.VALIDATION, () -> listings.updateListing(id, draft("Chairs", 5000),
                    List.of(ListingPhoto.keep(other))).join());
            assertEquals(List.of(), listings.getListing(id).join().listing().getImages());
        }
    }

    @Test
    void updateListing_databaseFailure_preservesPhotosAndRemovesImport() throws Exception {
        Path source = image("photo.png", 1, 1);
        try (ApplicationRuntime runtime = open()) {
            registerAndLogin(runtime, "alice");
            var listings = runtime.getListings();
            var original = listings.createListing(draft("Chairs", 5000), List.of(ListingPhoto.add(source)))
                    .join().listing();
            try (var connection = DriverManager.getConnection(
                    "jdbc:sqlite:" + directory.resolve("data/marketplace.db"));
                    var statement = connection.createStatement()) {
                statement.execute("CREATE TRIGGER fail_listing BEFORE UPDATE ON listings "
                        + "BEGIN SELECT RAISE(ABORT, 'simulated failure'); END");
            }
            assertFailure(ServiceException.Code.STORAGE, () -> listings.updateListing(original.getId(),
                    draft("Chair", 2500), List.of(ListingPhoto.add(source))).join());
            var unchanged = listings.getListing(original.getId()).join().listing();
            assertEquals("Chairs", unchanged.getDetails().title());
            assertEquals(filenames(original.getImages()), filenames(unchanged.getImages()));
            assertEquals(Set.copyOf(filenames(original.getImages())), savedFiles());
        }
    }

    @Test
    void deleteListing_withPhotos_removesFiles() throws Exception {
        Path source = image("photo.png", 1, 1);
        try (ApplicationRuntime runtime = open()) {
            registerAndLogin(runtime, "alice");
            UUID id = runtime.getListings().createListing(draft("Chairs", 5000), List.of(ListingPhoto.add(source)))
                    .join().listing().getId();
            runtime.getListings().deleteListing(id).join();
            assertEquals(Set.of(), savedFiles());
        }
    }

    @Test
    void open_orphanListingPhoto_removesItWithoutTouchingProfilePhotos() throws Exception {
        Path source = image("photo.png", 1, 1);
        String profile;
        try (ApplicationRuntime runtime = open()) {
            registerAndLogin(runtime, "alice");
            profile = runtime.getAccounts().replaceProfileImage(source).join().getProfileImage().orElseThrow();
        }
        Path orphan = photos().resolve(UUID.randomUUID() + ".png");
        Files.copy(source, orphan);
        try (ApplicationRuntime runtime = open()) {
            assertFalse(Files.exists(orphan));
            assertTrue(Files.exists(directory.resolve("data/images/profiles").resolve(profile)));
        }
    }

    private ApplicationRuntime open() throws Exception {
        return ApplicationRuntime.open(directory.resolve("data"), clock);
    }

    private Path photos() {
        return directory.resolve("data/images/listings");
    }

    private Set<String> savedFiles() throws Exception {
        try (var files = Files.list(photos())) {
            return files.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
        }
    }

    private Path image(String name, int width, int height) throws Exception {
        Path source = directory.resolve(name);
        assertTrue(ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png",
                source.toFile()));
        return source;
    }

    private static List<String> filenames(List<ListingImage> images) {
        return images.stream().map(ListingImage::filename).toList();
    }
}
