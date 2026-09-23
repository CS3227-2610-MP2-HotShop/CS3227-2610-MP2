package hotshop.service;

import java.util.Optional;
import java.util.UUID;

/** Deliberately excludes private pickup preferences and all credential information. */
public record PublicProfile(UUID id, String displayName, Optional<String> profileImage) {
}
