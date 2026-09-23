package hotshop.service;

/** Stable failure categories for callers; messages contain no persistence details. */
public final class AccountException extends RuntimeException {
    public enum Code {
        VALIDATION, AUTHENTICATION, SESSION, USERNAME_UNAVAILABLE, NOT_FOUND, STORAGE
    }

    private final Code code;

    /** Creates a categorized failure with a message suitable for display. */
    public AccountException(Code code, String message) {
        super(message);
        this.code = code;
    }

    /** Retains the underlying diagnostic cause separately from the display message. */
    public AccountException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }
}
