package hotshop.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ListingImageTest {
    @Test
    void constructor_relativeFilenameAndFirstPosition_acceptsImage() {
        ListingImage image = new ListingImage(" listings/chair.jpg ", 0);
        assertEquals("listings/chair.jpg", image.filename());
        assertEquals(0, image.displayOrder());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "/image.jpg", "../image.jpg", "a/../b", "./a", "a//b", "a/",
            "C:/image.jpg", "a\\b", "a\nb"})
    void constructor_unsafeFilename_throwsException(String filename) {
        assertThrows(IllegalArgumentException.class, () -> new ListingImage(filename, 0));
    }

    @Test
    void constructor_negativePosition_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> new ListingImage("a.jpg", -1));
    }

    @Test
    void constructor_nullFilename_throwsException() {
        assertThrows(NullPointerException.class, () -> new ListingImage(null, 0));
    }
}
