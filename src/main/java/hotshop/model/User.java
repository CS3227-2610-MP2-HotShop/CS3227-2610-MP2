package hotshop.model;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Identity and profile of a participant who can both buy and sell. */
public final class User {
    private final UUID id;
    private final String username;
    private final String displayName;
    private final String profileImage;
    private final String preferredPickupLocation;

    /**
     * Creates a profile; null image and pickup location mean absent. Credentials belong elsewhere.
     *
     * @param username the account username
     * @param displayName the display name, trimmed and limited to 80 Unicode code points
     * @param profileImage the storage-relative profile image filename, or null for no image
     * @param preferredPickupLocation the private pickup preference, or null to leave it absent
     * @throws IllegalArgumentException if the supplied values violate the model invariants
     * @throws NullPointerException if a required value is null
     */
    public User(String username, String displayName, String profileImage, String preferredPickupLocation) {
        this(UUID.randomUUID(), username, displayName, profileImage, preferredPickupLocation);
    }

    private User(UUID id, String username, String displayName, String profileImage, String preferredPickupLocation) {
        this.id = Objects.requireNonNull(id, "User ID");
        this.username = ModelValidation.text(username, 30, "Username");
        if (!this.username.matches("[A-Za-z0-9_]{3,30}")) {
            throw new IllegalArgumentException("Username must contain 3-30 ASCII letters, digits or underscores");
        }
        this.displayName = ModelValidation.text(displayName, 80, "Display name");
        this.profileImage = profileImage == null ? null : ModelValidation.filename(profileImage);
        this.preferredPickupLocation = preferredPickupLocation == null ? null
                : ModelValidation.text(preferredPickupLocation, 200, "Preferred pickup location");
    }

    /**
     * Restores a persisted profile while enforcing the same invariants as creation.
     *
     * @param id the user ID
     * @param username the account username
     * @param displayName the display name, trimmed and limited to 80 Unicode code points
     * @param profileImage the storage-relative profile image filename, or null for no image
     * @param preferredPickupLocation the private pickup preference, or null to leave it absent
     * @return the restored user with its persisted identity
     * @throws IllegalArgumentException if the supplied values violate the model invariants
     * @throws NullPointerException if a required value is null
     */
    public static User restore(UUID id, String username, String displayName,
            String profileImage, String preferredPickupLocation) {
        return new User(id, username, displayName, profileImage, preferredPickupLocation);
    }

    /**
     * Returns the stable identity of this record.
     *
     * @return the stable identity of this record
     */
    public UUID getId() {
        return id;
    }

    /**
     * Returns the trimmed username with its original letter case.
     *
     * @return the trimmed username with its original letter case
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns the username lowercased using the locale-independent root locale.
     *
     * @return the username lowercased using the locale-independent root locale
     */
    public String getNormalizedUsername() {
        return username.toLowerCase(Locale.ROOT);
    }

    /**
     * Returns the trimmed display name.
     *
     * @return the trimmed display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the storage-relative image filename, or empty if no image is set.
     *
     * @return the storage-relative image filename, or empty if no image is set
     */
    public Optional<String> getProfileImage() {
        return Optional.ofNullable(profileImage);
    }

    /**
     * Returns the private pickup preference, or empty if none is set.
     *
     * @return the private pickup preference, or empty if none is set
     */
    public Optional<String> getPreferredPickupLocation() {
        return Optional.ofNullable(preferredPickupLocation);
    }
}
