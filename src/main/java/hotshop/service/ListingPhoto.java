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

    /**
     * Selects an existing listing photo to retain in the ordered photo list.
     *
     * @param filename the storage-relative image filename
     * @return a reference to a photo already owned by the listing
     */
    static ListingPhoto keep(String filename) {
        return new Existing(filename);
    }

    /**
     * Selects a source file to import when the listing is saved.
     *
     * @param source the source image file
     * @return a request to import the source file
     */
    static ListingPhoto add(Path source) {
        return new NewFile(source);
    }
}
