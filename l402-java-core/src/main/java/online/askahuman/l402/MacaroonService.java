package online.askahuman.l402;

import com.github.nitram509.jmacaroons.Macaroon;
import com.github.nitram509.jmacaroons.MacaroonsBuilder;
import com.github.nitram509.jmacaroons.MacaroonsVerifier;

import java.security.MessageDigest;
import java.util.UUID;

/**
 * Creates and verifies L402 macaroons.
 *
 * <p>This is a pure Java class with no Spring dependencies.
 * Instantiate with a {@link MacaroonConfig}.</p>
 */
public class MacaroonService {

    private static final System.Logger log = System.getLogger(MacaroonService.class.getName());

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
        String identifier = paymentHash;
        long expiresAt = System.currentTimeMillis() / 1000 + expirySeconds;

        Macaroon macaroon = new MacaroonsBuilder(location, secretKey, identifier)
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
     * - All caveats satisfied (including expiration and tier)
     * - Preimage hashes to payment hash (SHA256)
     *
     * @param macaroonBase64       base64-encoded macaroon
     * @param preimageHex          hex-encoded preimage
     * @param expectedPaymentHash  hex-encoded payment hash
     * @param expectedTier         expected authorization tier
     * @return true if L402 credentials are valid
     */
    public boolean verifyL402(String macaroonBase64, String preimageHex, String expectedPaymentHash, String expectedTier) {
        try {
            Macaroon macaroon = MacaroonsBuilder.deserialize(macaroonBase64);

            String expiresAtStr = extractCaveatValue(macaroon, "expires_at");
            if (expiresAtStr != null) {
                long expiresAt = Long.parseLong(expiresAtStr);
                long currentTime = System.currentTimeMillis() / 1000;
                if (currentTime > expiresAt) {
                    log.log(System.Logger.Level.WARNING, "Macaroon expired - current: {0}, expires_at: {1}", currentTime, expiresAt);
                    return false;
                }
            }

            MacaroonsVerifier verifier = new MacaroonsVerifier(macaroon);
            verifier.satisfyGeneral(caveat -> caveat.startsWith("request_id = "));
            verifier.satisfyExact("payment_hash = " + expectedPaymentHash);
            verifier.satisfyGeneral(caveat -> caveat.startsWith("amount = "));
            verifier.satisfyExact("tier = " + expectedTier);
            verifier.satisfyGeneral(caveat -> caveat.startsWith("expires_at = "));
            verifier.satisfyGeneral(caveat -> caveat.startsWith("service = "));

            boolean macaroonValid = verifier.isValid(secretKey);
            boolean preimageValid = verifyPreimage(preimageHex, expectedPaymentHash);

            log.log(System.Logger.Level.DEBUG, "L402 verification - Macaroon valid: {0}, Preimage valid: {1}", macaroonValid, preimageValid);

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
        String prefix = key + " = ";
        for (var caveat : macaroon.caveatPackets) {
            String caveatValue = caveat.getValueAsText();
            if (caveatValue.startsWith(prefix)) {
                return caveatValue.substring(prefix.length());
            }
        }
        return null;
    }

    private boolean verifyPreimage(String preimageHex, String paymentHashHex) {
        try {
            byte[] preimage = hexToBytes(preimageHex);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(preimage);
            String computedHash = bytesToHex(hash);
            boolean valid = computedHash.equalsIgnoreCase(paymentHashHex);
            log.log(System.Logger.Level.DEBUG, "Preimage verification - Expected: {0}, Computed: {1}, Valid: {2}", paymentHashHex, computedHash, valid);
            return valid;
        } catch (Exception e) {
            log.log(System.Logger.Level.ERROR, "Preimage verification failed", e);
            return false;
        }
    }

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
