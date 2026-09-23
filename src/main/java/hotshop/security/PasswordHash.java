package hotshop.security;

/** Credential representation kept separate from shared User profiles. */
public record PasswordHash(String algorithm, int iterations, byte[] salt, byte[] hash) {
    /** Takes defensive copies so callers cannot mutate stored credential material. */
    public PasswordHash {
        salt = salt.clone();
        hash = hash.clone();
    }

    @Override
    public byte[] salt() {
        return salt.clone();
    }

    @Override
    public byte[] hash() {
        return hash.clone();
    }

    @Override
    public String toString() {
        return "PasswordHash[redacted]";
    }
}
