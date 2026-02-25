package online.askahuman.l402;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;
import java.security.MessageDigest;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MacaroonServiceTest {

    private static final String TEST_SECRET_KEY = "test-secret-key-for-macaroon-signing-12345678";
    private static final String TEST_LOCATION = "https://askahuman.online";
    private static final String TEST_PREIMAGE = "0000000000000000000000000000000000000000000000000000000000000001";
    private static final String TEST_PAYMENT_HASH = "ec4916dd28fc4c10d78e287ca5d9cc51ee1ae73cbfde08c6b37324cbfaac8bc5";

    private MacaroonService macaroonService;

    @BeforeEach
    void setUp() {
        macaroonService = new MacaroonService(new MacaroonConfig(TEST_SECRET_KEY, TEST_LOCATION, 3600));
    }

    @Test
    void testCreateAndVerifyMacaroon_roundtrip() {
        UUID requestId = UUID.randomUUID();
        String macaroon = macaroonService.createMacaroon(requestId, TEST_PAYMENT_HASH, 25, "tier_1");

        assertThat(macaroon).isNotNull().isNotBlank();
        boolean valid = macaroonService.verifyL402(macaroon, TEST_PREIMAGE, TEST_PAYMENT_HASH, "tier_1", 25);
        assertThat(valid).isTrue();
    }

    @Test
    void testVerifyL402_badPreimage() {
        UUID requestId = UUID.randomUUID();
        String macaroon = macaroonService.createMacaroon(requestId, TEST_PAYMENT_HASH, 25, "tier_1");

        // A preimage that doesn't hash to TEST_PAYMENT_HASH
        String wrongPreimage = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";
        boolean valid = macaroonService.verifyL402(macaroon, wrongPreimage, TEST_PAYMENT_HASH, "tier_1", 25);
        assertThat(valid).isFalse();
    }

    @Test
    void testVerifyL402_wrongSecretKey() {
        UUID requestId = UUID.randomUUID();
        String macaroon = macaroonService.createMacaroon(requestId, TEST_PAYMENT_HASH, 25, "tier_1");

        MacaroonService differentKeyService = new MacaroonService(
                new MacaroonConfig("different-secret-key-for-testing-purpose-12345678", TEST_LOCATION, 3600));
        boolean valid = differentKeyService.verifyL402(macaroon, TEST_PREIMAGE, TEST_PAYMENT_HASH, "tier_1", 25);
        assertThat(valid).isFalse();
    }

    @Test
    void testExtractRequestId() {
        UUID requestId = UUID.randomUUID();
        String macaroon = macaroonService.createMacaroon(requestId, TEST_PAYMENT_HASH, 25, "tier_1");

        UUID extracted = macaroonService.extractRequestId(macaroon);
        assertThat(extracted).isEqualTo(requestId);
    }

    @Test
    void testExtractPaymentHash() {
        UUID requestId = UUID.randomUUID();
        String macaroon = macaroonService.createMacaroon(requestId, TEST_PAYMENT_HASH, 25, "tier_1");

        String extracted = macaroonService.extractPaymentHash(macaroon);
        assertThat(extracted).isEqualTo(TEST_PAYMENT_HASH);
    }

    @Test
    void testExtractRequestId_invalidMacaroon() {
        assertThatThrownBy(() -> macaroonService.extractRequestId("not-a-valid-macaroon"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testExtractPaymentHash_invalidMacaroon() {
        assertThatThrownBy(() -> macaroonService.extractPaymentHash("not-a-valid-macaroon"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testVerifyL402_invalidMacaroon() {
        boolean valid = macaroonService.verifyL402("not-a-valid-macaroon", TEST_PREIMAGE, TEST_PAYMENT_HASH, "tier_1", 25);
        assertThat(valid).isFalse();
    }

    @Test
    void testVerifyL402_wrongPaymentHash() {
        UUID requestId = UUID.randomUUID();
        String macaroon = macaroonService.createMacaroon(requestId, TEST_PAYMENT_HASH, 25, "tier_1");

        String wrongHash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        boolean valid = macaroonService.verifyL402(macaroon, TEST_PREIMAGE, wrongHash, "tier_1", 25);
        assertThat(valid).isFalse();
    }

    @Test
    void testVerifyL402_wrongAmount() {
        UUID requestId = UUID.randomUUID();
        String macaroon = macaroonService.createMacaroon(requestId, TEST_PAYMENT_HASH, 25, "tier_1");

        // Verify against a different amount — satisfyExact("amount = 50") will fail
        boolean valid = macaroonService.verifyL402(macaroon, TEST_PREIMAGE, TEST_PAYMENT_HASH, "tier_1", 50);
        assertThat(valid).isFalse();
    }

    @Test
    void testCreateMacaroon_differentAmounts() {
        UUID requestId = UUID.randomUUID();

        String macaroon25 = macaroonService.createMacaroon(requestId, TEST_PAYMENT_HASH, 25, "tier_1");
        String macaroon50 = macaroonService.createMacaroon(requestId, TEST_PAYMENT_HASH, 50, "tier_1");

        // Different amounts produce different macaroons
        assertThat(macaroon25).isNotEqualTo(macaroon50);

        // Each verifies correctly only with its own amount
        assertThat(macaroonService.verifyL402(macaroon25, TEST_PREIMAGE, TEST_PAYMENT_HASH, "tier_1", 25)).isTrue();
        assertThat(macaroonService.verifyL402(macaroon50, TEST_PREIMAGE, TEST_PAYMENT_HASH, "tier_1", 50)).isTrue();

        // Cross-verification fails
        assertThat(macaroonService.verifyL402(macaroon25, TEST_PREIMAGE, TEST_PAYMENT_HASH, "tier_1", 50)).isFalse();
        assertThat(macaroonService.verifyL402(macaroon50, TEST_PREIMAGE, TEST_PAYMENT_HASH, "tier_1", 25)).isFalse();
    }

    @Test
    void testPreimageHashComputation() throws Exception {
        // Verify that SHA256(TEST_PREIMAGE_bytes) == TEST_PAYMENT_HASH
        // TEST_PREIMAGE is "0000...0001" in hex — 32 bytes
        byte[] preimageBytes = HexFormat.of().parseHex(TEST_PREIMAGE);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(preimageBytes);
        String computedHash = HexFormat.of().formatHex(hash);

        assertThat(computedHash).isEqualTo(TEST_PAYMENT_HASH);
    }

    @Test
    void testVerifyL402_expiredMacaroon() throws Exception {
        // Create a macaroon with 1-second TTL and wait for it to expire
        MacaroonService shortTtlService = new MacaroonService(
                new MacaroonConfig(TEST_SECRET_KEY, TEST_LOCATION, 1));
        UUID requestId = UUID.randomUUID();
        String macaroon = shortTtlService.createMacaroon(requestId, TEST_PAYMENT_HASH, 25, "tier_1");

        // Wait 2100ms to guarantee the Unix-second boundary is crossed
        // (expiresAt = now_seconds + 1; verification fails when currentTime > expiresAt)
        Thread.sleep(2100);

        boolean valid = shortTtlService.verifyL402(macaroon, TEST_PREIMAGE, TEST_PAYMENT_HASH, "tier_1", 25);
        assertThat(valid).isFalse();
    }

    @Test
    void testVerifyL402_wrongTier() {
        UUID requestId = UUID.randomUUID();
        String macaroon = macaroonService.createMacaroon(requestId, TEST_PAYMENT_HASH, 25, "tier_1");

        boolean valid = macaroonService.verifyL402(macaroon, TEST_PREIMAGE, TEST_PAYMENT_HASH, "tier_2", 25);
        assertThat(valid).isFalse();
    }

    @Test
    void testVerifyL402_invalidPreimageHex() {
        UUID requestId = UUID.randomUUID();
        String macaroon = macaroonService.createMacaroon(requestId, TEST_PAYMENT_HASH, 25, "tier_1");

        // Non-hex characters — rejected by length check (22 chars != 64) and hex parsing
        boolean valid = macaroonService.verifyL402(macaroon, "not-valid-hex-string!!", TEST_PAYMENT_HASH, "tier_1", 25);
        assertThat(valid).isFalse();
    }

    @Test
    void testVerifyL402_oversizedPreimage() {
        UUID requestId = UUID.randomUUID();
        String macaroon = macaroonService.createMacaroon(requestId, TEST_PAYMENT_HASH, 25, "tier_1");

        // Preimage exceeding 64 chars (Lightning Network preimage is always 32 bytes = 64 hex chars)
        String oversizedPreimage = "a".repeat(200);
        boolean valid = macaroonService.verifyL402(macaroon, oversizedPreimage, TEST_PAYMENT_HASH, "tier_1", 25);
        assertThat(valid).isFalse();
    }

    @Test
    void testVerifyL402_identifierMismatch() {
        UUID requestId = UUID.randomUUID();
        // Create macaroon with one payment hash
        String macaroon = macaroonService.createMacaroon(requestId, TEST_PAYMENT_HASH, 25, "tier_1");

        // Verify against a different payment hash — identifier in macaroon won't match expectedPaymentHash
        String differentHash = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        boolean valid = macaroonService.verifyL402(macaroon, TEST_PREIMAGE, differentHash, "tier_1", 25);
        assertThat(valid).isFalse();
    }
}
