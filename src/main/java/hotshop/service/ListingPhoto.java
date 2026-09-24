package hotshop.service;

import java.nio.file.Path;

/** One entry in a listing's complete ordered photo list: keep a saved photo or import a new file. */
public sealed interface ListingPhoto {
    /** A photo the listing already has, identified by its managed filename. */
    record Existing(String filename) implements ListingPhoto {
    }

    /** A file to validate and copy into managed storage; the original is never modified. */
    record NewFile(Path source) implements ListingPhoto {
    }

    static ListingPhoto keep(String filename) {
        return new Existing(filename);
    }

    static ListingPhoto add(Path source) {
        return new NewFile(source);
    }
}
