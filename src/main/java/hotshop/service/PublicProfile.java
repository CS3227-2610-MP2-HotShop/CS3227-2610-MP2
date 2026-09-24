package hotshop.service;

import java.util.Optional;
import java.util.UUID;

import hotshop.model.User;

/** Deliberately excludes private pickup preferences and all credential information. */
public record PublicProfile(UUID id, String displayName, Optional<String> profileImage) {
    /** Keeps only the fields any logged-in user may see. */
    static PublicProfile of(User user) {
        return new PublicProfile(user.getId(), user.getDisplayName(), user.getProfileImage());
    }
}
