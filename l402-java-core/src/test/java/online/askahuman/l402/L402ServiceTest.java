package online.askahuman.l402;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class L402ServiceTest {

    private static final String SECRET_KEY = "test-secret-key-for-l402-service-testing-12345";
    private static final String LOCATION = "https://askahuman.online";
    private static final String PAYMENT_HASH = "ec4916dd28fc4c10d78e287ca5d9cc51ee1ae73cbfde08c6b37324cbfaac8bc5";
    private static final String PAYMENT_REQUEST = "lnbc250n1p3test_bolt11_invoice_for_testing";
    private static final String VALID_PREIMAGE = "0000000000000000000000000000000000000000000000000000000000000001";

    @Mock
    private LightningClient lightningClient;

    private AutoCloseable mocks;
    private MacaroonService macaroonService;
    private L402Service l402Service;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        macaroonService = new MacaroonService(new MacaroonConfig(SECRET_KEY, LOCATION, 3600));
        l402Service = new L402Service(macaroonService, lightningClient);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    private L402PaymentContext mockContext() {
        UUID id = UUID.randomUUID();
        return new L402PaymentContext() {
            @Override public UUID requestId() { return id; }
            @Override public String paymentHash() { return PAYMENT_HASH; }
            @Override public String paymentRequest() { return PAYMENT_REQUEST; }
            @Override public int amountSats() { return 25; }
            @Override public String tier() { return "tier_1"; }
        };
    }

    @Test
    void testCreateChallenge_returnsValidPaymentChallenge() {
        L402PaymentContext ctx = mockContext();

        PaymentChallenge challenge = l402Service.createChallenge(ctx);

        assertThat(challenge.scheme()).isEqualTo("L402");
        assertThat(challenge.wwwAuthenticate()).startsWith("L402 macaroon=");
        assertThat(challenge.wwwAuthenticate()).contains("invoice=");
        assertThat(challenge.details().get("invoice")).isEqualTo(PAYMENT_REQUEST);
        assertThat(challenge.details().get("paymentHash")).isEqualTo(PAYMENT_HASH);
        assertThat(challenge.details().get("amountSats")).isEqualTo(25);
        assertThat(challenge.details().get("macaroon")).isNotNull();
    }

    @Test
    void testVerifyCredential_validL402ReturnsTrue() {
        L402PaymentContext ctx = mockContext();

        // Create real macaroon so we can build a valid credential
        String macaroon = macaroonService.createMacaroon(ctx.requestId(), PAYMENT_HASH, 25, "tier_1");
        String credential = macaroon + ":" + VALID_PREIMAGE;

        boolean result = l402Service.verifyCredential(ctx, credential);
        assertThat(result).isTrue();
    }

    @Test
    void testVerifyCredential_invalidFormatReturnsFalse() {
        L402PaymentContext ctx = mockContext();

        // No colon separator — credential.split(":", 2) yields only one part
        boolean result = l402Service.verifyCredential(ctx, "macaroon-without-colon-separator");
        assertThat(result).isFalse();
    }

    @Test
    void testVerifyCredential_invalidMacaroonReturnsFalse() {
        L402PaymentContext ctx = mockContext();

        // Garbage macaroon but correct format (has colon)
        boolean result = l402Service.verifyCredential(ctx, "not-a-real-macaroon:" + VALID_PREIMAGE);
        assertThat(result).isFalse();
    }

    @Test
    void testVerifyCredential_invalidPreimageReturnsFalse() {
        L402PaymentContext ctx = mockContext();

        String macaroon = macaroonService.createMacaroon(ctx.requestId(), PAYMENT_HASH, 25, "tier_1");
        // Preimage doesn't hash to PAYMENT_HASH
        String wrongPreimage = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";
        String credential = macaroon + ":" + wrongPreimage;

        boolean result = l402Service.verifyCredential(ctx, credential);
        assertThat(result).isFalse();
    }

    @Test
    void testVerifyCredential_exceptionReturnsFalse() {
        L402PaymentContext ctx = mockContext();

        // Completely invalid credential string triggers exception in macaroon deserialization
        boolean result = l402Service.verifyCredential(ctx, "!!invalid!!:!!also-invalid!!");
        assertThat(result).isFalse();
    }

    @Test
    void testGetPaymentStatus_invoicePaidReturnsPaidStatus() {
        when(lightningClient.isInvoicePaid(PAYMENT_HASH)).thenReturn(true);

        PaymentStatus status = l402Service.getPaymentStatus(PAYMENT_HASH);

        assertThat(status.paid()).isTrue();
        assertThat(status.scheme()).isEqualTo("L402");
        assertThat(status.details()).isEqualTo("Payment confirmed");
    }

    @Test
    void testGetPaymentStatus_invoiceUnpaidReturnsUnpaidStatus() {
        when(lightningClient.isInvoicePaid(PAYMENT_HASH)).thenReturn(false);

        PaymentStatus status = l402Service.getPaymentStatus(PAYMENT_HASH);

        assertThat(status.paid()).isFalse();
        assertThat(status.scheme()).isEqualTo("L402");
        assertThat(status.details()).isEqualTo("Invoice not yet paid");
    }

    @Test
    void testCreateChallenge_detailsDoNotContainFeeFields() {
        L402PaymentContext ctx = mockContext();

        PaymentChallenge challenge = l402Service.createChallenge(ctx);

        // Fee fields are domain-level, not L402 protocol-level
        assertThat(challenge.details()).doesNotContainKey("platformFee");
        assertThat(challenge.details()).doesNotContainKey("verifierPayout");
        assertThat(challenge.details()).doesNotContainKey("totalAmount");
    }
}
