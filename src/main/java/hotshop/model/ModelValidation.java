package hotshop.model;

import java.util.Objects;

/** Shared, package-local validation of model text and storage-relative image names. */
final class ModelValidation {
    private ModelValidation() {
    }

    static String text(String value, int maximumLength, String field) {
        String trimmed = Objects.requireNonNull(value, field).strip();
        int length = trimmed.codePointCount(0, trimmed.length());
        if (length == 0 || length > maximumLength) {
            throw new IllegalArgumentException(field + " must contain 1-" + maximumLength + " characters");
        }
        return trimmed;
    }

    static String filename(String value) {
        String filename = Objects.requireNonNull(value, "Filename").strip();
        if (filename.isEmpty() || filename.contains("\\") || filename.contains(":")
                || filename.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Image name must be a relative, slash-separated filename");
        }
        for (String segment : filename.split("/", -1)) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Image name must not contain empty or traversal segments");
            }
        }
        return filename;
    }
}
