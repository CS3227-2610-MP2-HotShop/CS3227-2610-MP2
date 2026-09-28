package hotshop.security;

/** Credential representation kept separate from shared User profiles. */
public record PasswordHash(String algorithm, int iterations, byte[] salt, byte[] hash) {
    /**
     * Takes defensive copies so callers cannot mutate stored credential material.
     *
     * @param algorithm the password derivation algorithm name
     * @param iterations the password derivation iteration count
     * @param salt the salt bytes, copied defensively
     * @param hash the derived key bytes, copied defensively
     */
    public PasswordHash {
        salt = salt.clone();
        hash = hash.clone();
    }

    /**
     * Returns a defensive copy of the salt.
     *
     * @return a defensive copy of the salt
     */
    @Override
    public byte[] salt() {
        return salt.clone();
    }

    /**
     * Returns a defensive copy of the derived key.
     *
     * @return a defensive copy of the derived key
     */
    @Override
    public byte[] hash() {
        return hash.clone();
    }

    /**
     * Returns a redacted description without exposing credential material.
     *
     * @return a redacted credential description
     */
    @Override
    public String toString() {
        return "PasswordHash[redacted]";
    }
}
