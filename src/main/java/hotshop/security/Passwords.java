package hotshop.security;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import hotshop.service.AccountException;

/** Password policy and versioned, salted PBKDF2 storage. */
public final class Passwords {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 600_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 128;
    private final SecureRandom random = new SecureRandom();

    /** Validates the exact password and derives a key using a fresh random salt. */
    public PasswordHash hash(String password) {
        validate(password);
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        return new PasswordHash(ALGORITHM, ITERATIONS, salt, derive(password, salt, ITERATIONS));
    }

    /** Compares a supplied password against supported persisted credentials without changing them. */
    public boolean matches(String password, PasswordHash stored) {
        if (password == null || password.codePointCount(0, password.length()) > MAX_LENGTH) {
            return false;
        }
        if (!ALGORITHM.equals(stored.algorithm()) || stored.iterations() < ITERATIONS
                || stored.iterations() > ITERATIONS * 10 || stored.salt().length != SALT_BYTES
                || stored.hash().length != HASH_BITS / Byte.SIZE) {
            throw new AccountException(AccountException.Code.STORAGE, "Unsupported credential format");
        }
        byte[] actual = derive(password, stored.salt(), stored.iterations());
        try {
            return MessageDigest.isEqual(actual, stored.hash());
        } finally {
            Arrays.fill(actual, (byte) 0);
        }
    }

    private void validate(String password) {
        if (password == null) {
            throw invalidPassword();
        }
        int length = password.codePointCount(0, password.length());
        boolean hasUppercase = password.chars().anyMatch(value -> value >= 'A' && value <= 'Z');
        boolean hasLowercase = password.chars().anyMatch(value -> value >= 'a' && value <= 'z');
        boolean hasDigit = password.chars().anyMatch(value -> value >= '0' && value <= '9');
        boolean hasPunctuation = password.chars().anyMatch(value -> value >= '!' && value <= '/'
                || value >= ':' && value <= '@' || value >= '[' && value <= '`' || value >= '{' && value <= '~');
        if (length < MIN_LENGTH || length > MAX_LENGTH
                || !hasUppercase || !hasLowercase || !hasDigit || !hasPunctuation) {
            throw invalidPassword();
        }
    }

    private AccountException invalidPassword() {
        return new AccountException(AccountException.Code.VALIDATION,
                "Password requires 8-128 characters with uppercase, lowercase, digit and ASCII punctuation");
    }

    private byte[] derive(String password, byte[] salt, int iterations) {
        char[] characters = password.toCharArray();
        PBEKeySpec specification = new PBEKeySpec(characters, salt, iterations, HASH_BITS);
        Arrays.fill(characters, '\0');
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(specification).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new AccountException(AccountException.Code.STORAGE, "Password storage is unavailable", exception);
        } finally {
            specification.clearPassword();
        }
    }
}
