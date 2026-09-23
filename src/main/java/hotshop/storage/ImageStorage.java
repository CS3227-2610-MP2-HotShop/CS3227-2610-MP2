package hotshop.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** Managed image files; each feature supplies its own bounded import policy. */
public final class ImageStorage {
    public static final Limits PROFILE_LIMITS = new Limits(5 * 1024 * 1024, 512, 512);
    public static final Limits LISTING_LIMITS = new Limits(10 * 1024 * 1024, 4096, 4096);
    private static final String MANAGED_NAME =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpeg)";
    private final Path root;

    /** Positive import bounds chosen by the feature using this storage namespace. */
    public record Limits(int maximumBytes, int maximumWidth, int maximumHeight) {
        /** Rejects nonpositive bounds before a policy can be used. */
        public Limits {
            if (maximumBytes < 1 || maximumWidth < 1 || maximumHeight < 1) {
                throw new IllegalArgumentException("Image limits must be positive");
            }
        }
    }

    /** Creates and resolves one managed namespace, rejecting a symbolic-link directory. */
    public ImageStorage(Path directory) throws IOException {
        Files.createDirectories(directory);
        if (Files.isSymbolicLink(directory)) {
            throw new IOException("Managed image directory must not be a symbolic link");
        }
        root = directory.toRealPath();
    }

    /** Copies the same bounded bytes that were validated, independent of later source edits. */
    public String importImage(Path source, Limits limits) throws IOException {
        if (source == null || !Files.isRegularFile(source)) {
            throw new IllegalArgumentException("Select a readable image file");
        }
        byte[] bytes;
        try (var input = Files.newInputStream(source)) {
            bytes = input.readNBytes(limits.maximumBytes() + 1);
        }
        if (bytes.length > limits.maximumBytes()) {
            throw new IllegalArgumentException("Image file exceeds the size limit");
        }
        String format = validate(bytes, limits);
        String name = UUID.randomUUID() + "." + format;
        Path destination = resolve(name);
        try {
            Files.write(destination, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException failure) {
            try {
                Files.deleteIfExists(destination);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
        return name;
    }

    private String validate(byte[] bytes, Limits limits) {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("Image must contain JPEG or PNG data");
            }
            var reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("jpeg") && !format.equals("png")) {
                    throw new IllegalArgumentException("Image must contain JPEG or PNG data");
                }
                reader.setInput(input);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > limits.maximumWidth() || height > limits.maximumHeight()) {
                    throw new IllegalArgumentException("Image dimensions exceed the permitted limits");
                }
                reader.addIIOReadWarningListener((source, warning) -> {
                    throw new IllegalArgumentException("Image data is incomplete or damaged");
                });
                reader.read(0);
                return format;
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Image data is incomplete or damaged", exception);
        }
    }

    /** Only generated flat names are accepted; files outside this namespace are never removed. */
    public Path resolve(String name) {
        if (name == null || !name.matches(MANAGED_NAME)) {
            throw new IllegalArgumentException("Invalid managed image name");
        }
        Path path = root.resolve(name);
        if (Files.isSymbolicLink(path)) {
            throw new IllegalArgumentException("Managed image must not be a symbolic link");
        }
        return path;
    }

    /** Deletes only a regular generated file within this namespace; absence is a successful no-op. */
    public void delete(String name) throws IOException {
        Path file = resolve(name);
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Managed image is not a regular file");
        }
        Files.deleteIfExists(file);
    }

    /** Enumerates generated names only; unrelated files in the directory are excluded. */
    public Set<String> listManagedFiles() throws IOException {
        try (var files = Files.list(root)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.matches(MANAGED_NAME))
                    .collect(Collectors.toUnmodifiableSet());
        }
    }
}
