package online.askahuman.l402;

import com.github.nitram509.jmacaroons.Macaroon;
import com.github.nitram509.jmacaroons.MacaroonsBuilder;
import com.github.nitram509.jmacaroons.MacaroonsVerifier;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Creates and verifies L402 macaroons.
 *
 * <p>This is a pure Java class with no Spring dependencies.
 * Instantiate with a {@link MacaroonConfig}.</p>
 *
 * <p><strong>Security:</strong> The {@code preimageHex} argument passed to verification methods
 * is the Lightning Network proof-of-payment and must never appear in log output.
 * Its exposure allows credential replay within the macaroon's TTL window.</p>
 */
public final class MacaroonService {

    private static final System.Logger log = System.getLogger(MacaroonService.class.getName());

    /**
     * Maximum accepted hex-string length (128 chars = 64 raw bytes, the SHA-256 output size).
     * Rejects oversized input before parsing to prevent resource amplification attacks.
     */
    private static final int MAX_HEX_LENGTH = 128;

    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private final String secretKey;
    private final String location;
    private final int expirySeconds;

    /**
     * Create a MacaroonService with the given configuration.
     */
    public MacaroonService(MacaroonConfig config) {
        this.secretKey = config.getSecretKey();
        this.location = config.getLocation();
        this.expirySeconds = config.getExpirySeconds();
    }

    /**
     * Create L402 macaroon per spec:
     * - Identifier: payment hash (not request ID)
     * - Caveats: request_id, payment_hash, amount, tier, expires_at, service
     *
     * @param requestId   verification request UUID (stored as caveat)
     * @param paymentHash Lightning payment hash (used as identifier per L402 spec)
     * @param amountSats  invoice amount
     * @param tier        authorization scope (e.g., "tier_1")
     * @return serialized macaroon (base64)
     */
    public String createMacaroon(UUID requestId, String paymentHash, int amountSats, String tier) {
        long expiresAt = System.currentTimeMillis() / 1000 + expirySeconds;

        Macaroon macaroon = new MacaroonsBuilder(location, secretKey, paymentHash)
                .add_first_party_caveat("request_id = " + requestId.toString())
                .add_first_party_caveat("payment_hash = " + paymentHash)
                .add_first_party_caveat("amount = " + amountSats)
                .add_first_party_caveat("tier = " + tier)
                .add_first_party_caveat("expires_at = " + expiresAt)
                .add_first_party_caveat("service = verification")
                .getMacaroon();

        return macaroon.serialize();
    }

    /**
     * Verify L402 credentials (macaroon + preimage) per L402 spec:
     * - Macaroon signature valid
     * - All caveats satisfied (including expiration, tier, and amount)
     * - Preimage hashes to payment hash (SHA256)
     *
     * @param macaroonBase64      base64-encoded macaroon
     * @param preimageHex         hex-encoded preimage
     * @param expectedPaymentHash hex-encoded payment hash
     * @param expectedTier        expected authorization tier
     * @param expectedAmountSats  expected invoice amount in satoshis
     * @return true if L402 credentials are valid
     */
    public boolean verifyL402(String macaroonBase64, String preimageHex,
                               String expectedPaymentHash, String expectedTier,
                               int expectedAmountSats) {
        try {
            Macaroon macaroon = MacaroonsBuilder.deserialize(macaroonBase64);

            // Per L402 spec: identifier must equal the payment hash.
            // Compare raw bytes (not hex strings) for true constant-time behaviour regardless of
            // string length; a length mismatch on hex strings would leak timing information.
            try {
                byte[] expectedHashBytes = hexToBytes(expectedPaymentHash);
                byte[] identifierBytes = hexToBytes(macaroon.identifier);
                if (!MessageDigest.isEqual(expectedHashBytes, identifierBytes)) {
                    log.log(System.Logger.Level.WARNING, "Macaroon identifier mismatch");
                    return false;
                }
            } catch (IllegalArgumentException e) {
                log.log(System.Logger.Level.WARNING,
                        "Macaroon identifier or expected payment hash is not valid hex");
                return false;
            }

            String expiresAtStr = extractCaveatValue(macaroon, "expires_at");
            if (expiresAtStr != null) {
                long expiresAt = Long.parseLong(expiresAtStr);
                long currentTime = System.currentTimeMillis() / 1000;
                if (currentTime > expiresAt) {
                    log.log(System.Logger.Level.WARNING,
                            "Macaroon expired - current: {0}, expires_at: {1}", currentTime, expiresAt);
                    return false;
                }
            }

            MacaroonsVerifier verifier = new MacaroonsVerifier(macaroon);
            verifier.satisfyGeneral(caveat -> caveat.startsWith("request_id = "));
            verifier.satisfyExact("payment_hash = " + expectedPaymentHash);
            verifier.satisfyExact("amount = " + expectedAmountSats);
            verifier.satisfyExact("tier = " + expectedTier);
            verifier.satisfyGeneral(caveat -> caveat.startsWith("expires_at = "));
            verifier.satisfyGeneral(caveat -> caveat.startsWith("service = "));

            boolean macaroonValid = verifier.isValid(secretKey);
            boolean preimageValid = verifyPreimage(preimageHex, expectedPaymentHash);

            log.log(System.Logger.Level.DEBUG,
                    "L402 verification - Macaroon valid: {0}, Preimage valid: {1}",
                    macaroonValid, preimageValid);

            return macaroonValid && preimageValid;

        } catch (Exception e) {
            log.log(System.Logger.Level.ERROR, "L402 verification failed", e);
            return false;
        }
    }

    /**
     * Extract request ID from macaroon.
     * Per L402 spec: request_id is stored as a caveat (not identifier).
     */
    public UUID extractRequestId(String macaroonBase64) {
        try {
            Macaroon macaroon = MacaroonsBuilder.deserialize(macaroonBase64);
            String requestIdStr = extractCaveatValue(macaroon, "request_id");
            if (requestIdStr == null) {
                throw new IllegalArgumentException("No request_id found in macaroon");
            }
            return UUID.fromString(requestIdStr);
        } catch (Exception e) {
            log.log(System.Logger.Level.ERROR, "Failed to extract request ID from macaroon", e);
            throw new IllegalArgumentException("Invalid macaroon format");
        }
    }

    /**
     * Extract payment hash from macaroon.
     * Per L402 spec: payment hash is both the identifier AND a caveat.
     */
    public String extractPaymentHash(String macaroonBase64) {
        try {
            Macaroon macaroon = MacaroonsBuilder.deserialize(macaroonBase64);
            return macaroon.identifier;
        } catch (Exception e) {
            log.log(System.Logger.Level.ERROR, "Failed to extract payment hash from macaroon", e);
            throw new IllegalArgumentException("Invalid macaroon format");
        }
    }

    private String extractCaveatValue(Macaroon macaroon, String key) {
        if (macaroon.caveatPackets == null) {
            return null;
        }
        String prefix = key + " = ";
        for (var caveat : macaroon.caveatPackets) {
            String caveatValue = caveat.getValueAsText();
            if (caveatValue.startsWith(prefix)) {
                return caveatValue.substring(prefix.length());
            }
        }
        return null;
    }

    /**
     * Verify that SHA-256(preimage) == paymentHash.
     *
     * <p><strong>Security:</strong> {@code preimageHex} is the Lightning Network proof-of-payment.
     * It must never be logged; its exposure enables credential replay. This method hashes the
     * preimage and discards the raw value immediately.</p>
     */
    private boolean verifyPreimage(String preimageHex, String paymentHashHex) {
        // Lightning Network preimage is exactly 32 bytes (64 hex chars); reject anything else
        // to prevent resource amplification via oversized input.
        if (preimageHex == null || preimageHex.length() != 64) {
            log.log(System.Logger.Level.WARNING, "Preimage has invalid length: expected 64 hex chars");
            return false;
        }
        try {
            byte[] preimage = hexToBytes(preimageHex);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(preimage);
            // Constant-time comparison on raw bytes to prevent timing side-channel attacks
            return MessageDigest.isEqual(hash, hexToBytes(paymentHashHex));
        } catch (Exception e) {
            log.log(System.Logger.Level.ERROR, "Preimage verification failed", e);
            return false;
        }
    }

    private byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() % 2 != 0 || hex.length() > MAX_HEX_LENGTH) {
            throw new IllegalArgumentException(
                    "Invalid hex string: null, odd length, or exceeds maximum (" + MAX_HEX_LENGTH + " chars)");
        }
        try {
            return HEX_FORMAT.parseHex(hex);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid hex string: " + e.getMessage());
        }
    }
}
