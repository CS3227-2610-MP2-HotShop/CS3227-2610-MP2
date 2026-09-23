package hotshop.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ImageStorageTest {
    @TempDir
    Path directory;

    @ParameterizedTest
    @CsvSource({"1,1", "512,512", "512,256", "256,512"})
    void importImage_boundaryDimensions_acceptsImage(int width, int height) throws Exception {
        Path source = image("png", width, height);
        ImageStorage storage = new ImageStorage(directory.resolve("managed"));
        assertTrue(Files.exists(storage.resolve(storage.importImage(source, ImageStorage.PROFILE_LIMITS))));
    }

    @ParameterizedTest
    @CsvSource({"513,512", "512,513"})
    void importImage_oversizedDimension_rejectsImage(int width, int height) throws Exception {
        Path source = image("png", width, height);
        ImageStorage storage = new ImageStorage(directory.resolve("managed"));
        assertThrows(IllegalArgumentException.class, () -> storage.importImage(source, ImageStorage.PROFILE_LIMITS));
        assertTrue(storage.listManagedFiles().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"jpeg", "png"})
    void importImage_misleadingExtension_checksActualContent(String format) throws Exception {
        Path source = image(format, 1, 1);
        Path renamed = Files.move(source, directory.resolve("photo.txt"));
        ImageStorage storage = new ImageStorage(directory.resolve("managed"));
        assertTrue(storage.importImage(renamed, ImageStorage.PROFILE_LIMITS).endsWith("." + format));
    }

    @Test
    void importImage_gifContent_rejectsUnsupportedFormat() throws Exception {
        Path source = image("gif", 1, 1);
        ImageStorage storage = new ImageStorage(directory.resolve("managed"));
        assertThrows(IllegalArgumentException.class, () -> storage.importImage(source, ImageStorage.PROFILE_LIMITS));
    }

    @Test
    void importImage_corruptPng_rejectsImage() throws Exception {
        Path source = image("png", 512, 512);
        byte[] original = Files.readAllBytes(source);
        Files.write(source, java.util.Arrays.copyOf(original, original.length / 2));
        ImageStorage storage = new ImageStorage(directory.resolve("managed"));
        assertThrows(IllegalArgumentException.class, () -> storage.importImage(source, ImageStorage.PROFILE_LIMITS));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void importImage_fileSizeBoundary_enforcesByteLimit(int excessBytes) throws Exception {
        Path source = image("png", 1, 1);
        byte[] original = Files.readAllBytes(source);
        Files.write(source, java.util.Arrays.copyOf(original, 5 * 1024 * 1024 + excessBytes));
        ImageStorage storage = new ImageStorage(directory.resolve("managed"));
        if (excessBytes == 0) {
            assertTrue(Files.exists(storage.resolve(storage.importImage(source, ImageStorage.PROFILE_LIMITS))));
        } else {
            assertThrows(IllegalArgumentException.class,
                    () -> storage.importImage(source, ImageStorage.PROFILE_LIMITS));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"../source.png", "photo.png", "/tmp/source.png"})
    void delete_unmanagedName_rejectsPath(String filename) throws Exception {
        ImageStorage storage = new ImageStorage(directory.resolve("managed"));
        assertThrows(IllegalArgumentException.class, () -> storage.delete(filename));
    }

    private Path image(String format, int width, int height) throws Exception {
        Path source = directory.resolve("source." + format);
        assertTrue(ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB),
                format, source.toFile()));
        return source;
    }

    @Test
    void importImage_validImage_copiesAndDeletesOnlyManagedFile() throws Exception {
        Path source = directory.resolve("source.png");
        ImageIO.write(new BufferedImage(512, 256, BufferedImage.TYPE_INT_RGB), "png", source.toFile());
        ImageStorage storage = new ImageStorage(directory.resolve("managed"));
        String name = storage.importImage(source, ImageStorage.PROFILE_LIMITS);
        assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(storage.resolve(name)));
        storage.delete(name);
        assertFalse(Files.exists(storage.resolve(name)));
        assertTrue(Files.exists(source));
    }
}
