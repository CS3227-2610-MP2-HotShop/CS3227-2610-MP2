package hotshop.service;

/** Failures shared by every service, with stable categories; messages contain no persistence details. */
public final class ServiceException extends RuntimeException {
    /**
     * PERMISSION means the current user does not own the record; INVALID_STATE means the
     * record's status does not allow the action.
     */
    public enum Code {
        VALIDATION, AUTHENTICATION, SESSION, USERNAME_UNAVAILABLE, NOT_FOUND, STORAGE, PERMISSION, INVALID_STATE
    }

    private final Code code;

    /**
     * Creates a categorized failure with a message suitable for display.
     *
     * @param code the stable failure category
     * @param message the displayable failure message
     */
    public ServiceException(Code code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * Retains the underlying diagnostic cause separately from the display message.
     *
     * @param code the stable failure category
     * @param message the displayable failure message
     * @param cause the underlying diagnostic cause
     */
    public ServiceException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    static ServiceException validation(String message) {
        return new ServiceException(Code.VALIDATION, message);
    }

    static ServiceException notFound(String message) {
        return new ServiceException(Code.NOT_FOUND, message);
    }

    static ServiceException permission(String message) {
        return new ServiceException(Code.PERMISSION, message);
    }

    static ServiceException invalidState(String message) {
        return new ServiceException(Code.INVALID_STATE, message);
    }

    /**
     * Returns the stable failure category.
     *
     * @return the stable failure category
     */
    public Code getCode() {
        return code;
    }
}
