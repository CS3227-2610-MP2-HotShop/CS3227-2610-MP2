package hotshop.model;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Identity and profile of a participant who can both buy and sell. */
public final class User {
    private final UUID id = UUID.randomUUID();
    private final String username;
    private final String displayName;
    private final String profileImage;
    private final String preferredPickupLocation;

    /** Creates a profile; null image and pickup location mean absent. Credentials belong elsewhere. */
    public User(String username, String displayName, String profileImage, String preferredPickupLocation) {
        this.username = ModelValidation.text(username, 30, "Username");
        if (!this.username.matches("[A-Za-z0-9_]{3,30}")) {
            throw new IllegalArgumentException("Username must contain 3-30 ASCII letters, digits or underscores");
        }
        this.displayName = ModelValidation.text(displayName, 80, "Display name");
        this.profileImage = profileImage == null ? null : ModelValidation.filename(profileImage);
        this.preferredPickupLocation = preferredPickupLocation == null ? null
                : ModelValidation.text(preferredPickupLocation, 200, "Preferred pickup location");
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getNormalizedUsername() {
        return username.toLowerCase(Locale.ROOT);
    }

    public String getDisplayName() {
        return displayName;
    }

    public Optional<String> getProfileImage() {
        return Optional.ofNullable(profileImage);
    }

    public Optional<String> getPreferredPickupLocation() {
        return Optional.ofNullable(preferredPickupLocation);
    }
}
