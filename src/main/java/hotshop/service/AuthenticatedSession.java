package hotshop.service;

import java.util.Optional;
import java.util.UUID;

/** Application identity; read and mutate only inside the shared service worker. */
public final class AuthenticatedSession {
    private UUID userId;

    /**
     * Returns the current user ID, or empty when logged out.
     *
     * @return the current user ID, or empty when logged out
     */
    public Optional<UUID> getCurrentUserId() {
        return Optional.ofNullable(userId);
    }

    /**
     * Returns the acting user on the shared worker, or fails when nobody is logged in.
     *
     * @return the current authenticated user ID
     * @throws ServiceException with SESSION code if nobody is logged in
     */
    public UUID requireUserId() {
        if (userId == null) {
            throw new ServiceException(ServiceException.Code.SESSION, "Login is required");
        }
        return userId;
    }

    void requireLoggedOut() {
        if (userId != null) {
            throw new ServiceException(ServiceException.Code.SESSION, "Log out before switching accounts");
        }
    }

    void login(UUID id) {
        requireLoggedOut();
        userId = id;
    }

    void logout() {
        userId = null;
    }
}
