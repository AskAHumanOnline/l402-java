package online.askahuman.l402;

/**
 * Configuration for the L402 Macaroon service.
 * Pass an instance to {@link MacaroonService} constructor.
 */
public final class MacaroonConfig {

    private final String secretKey;
    private final String location;
    private final int expirySeconds;

    /**
     * @param secretKey     secret used to sign macaroons (keep it safe!)
     * @param location      service location identifier embedded in macaroons
     * @param expirySeconds TTL for newly created macaroons
     */
    public MacaroonConfig(String secretKey, String location, int expirySeconds) {
        if (secretKey == null || secretKey.length() < 32) {
            throw new IllegalArgumentException("secretKey must be at least 32 characters");
        }
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("location must not be null or blank");
        }
        if (expirySeconds <= 0) {
            throw new IllegalArgumentException("expirySeconds must be positive");
        }
        this.secretKey = secretKey;
        this.location = location;
        this.expirySeconds = expirySeconds;
    }

    public String getSecretKey() { return secretKey; }
    public String getLocation() { return location; }
    public int getExpirySeconds() { return expirySeconds; }
}
