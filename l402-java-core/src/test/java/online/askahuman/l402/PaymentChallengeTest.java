package online.askahuman.l402;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentChallengeTest {

    private static final String MACAROON = "dGVzdC1tYWNhcm9vbg==";
    private static final String INVOICE = "lnbc250n1p3xyzabc123";
    private static final String PAYMENT_HASH = "ec4916dd28fc4c10d78e287ca5d9cc51ee1ae73cbfde08c6b37324cbfaac8bc5";
    private static final int AMOUNT_SATS = 25;

    @Test
    void l402_shouldBuildCorrectChallenge() {
        PaymentChallenge challenge = PaymentChallenge.l402(MACAROON, INVOICE, PAYMENT_HASH, AMOUNT_SATS);

        assertThat(challenge.scheme()).isEqualTo("L402");
        assertThat(challenge.wwwAuthenticate()).startsWith("L402 macaroon=");
        assertThat(challenge.details()).containsKey("macaroon");
        assertThat(challenge.details()).containsKey("invoice");
        assertThat(challenge.details()).containsKey("paymentHash");
        assertThat(challenge.details()).containsKey("amountSats");
    }

    @Test
    void l402_wwwAuthenticateFormat() {
        PaymentChallenge challenge = PaymentChallenge.l402(MACAROON, INVOICE, PAYMENT_HASH, AMOUNT_SATS);

        String expected = String.format("L402 macaroon=\"%s\", invoice=\"%s\"", MACAROON, INVOICE);
        assertThat(challenge.wwwAuthenticate()).isEqualTo(expected);
    }

    @Test
    void l402_detailsContainCorrectValues() {
        PaymentChallenge challenge = PaymentChallenge.l402(MACAROON, INVOICE, PAYMENT_HASH, AMOUNT_SATS);

        assertThat(challenge.details().get("macaroon")).isEqualTo(MACAROON);
        assertThat(challenge.details().get("invoice")).isEqualTo(INVOICE);
        assertThat(challenge.details().get("paymentHash")).isEqualTo(PAYMENT_HASH);
        assertThat(challenge.details().get("amountSats")).isEqualTo(AMOUNT_SATS);
    }

    @Test
    void recordComponents_shouldBeAccessible() {
        Map<String, Object> detailsMap = Map.of("key", "value");
        PaymentChallenge challenge = new PaymentChallenge("CUSTOM", "CUSTOM macaroon=\"x\"", detailsMap);

        assertThat(challenge.scheme()).isEqualTo("CUSTOM");
        assertThat(challenge.wwwAuthenticate()).isEqualTo("CUSTOM macaroon=\"x\"");
        assertThat(challenge.details()).isEqualTo(detailsMap);
    }

    @Test
    void l402_detailsMapIsImmutable() {
        PaymentChallenge challenge = PaymentChallenge.l402(MACAROON, INVOICE, PAYMENT_HASH, AMOUNT_SATS);

        assertThatThrownBy(() -> challenge.details().put("newKey", "newValue"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void l402_equalityViaSameParams() {
        PaymentChallenge challenge1 = PaymentChallenge.l402(MACAROON, INVOICE, PAYMENT_HASH, AMOUNT_SATS);
        PaymentChallenge challenge2 = PaymentChallenge.l402(MACAROON, INVOICE, PAYMENT_HASH, AMOUNT_SATS);

        // Records use structural equality — same params must produce equal instances
        assertThat(challenge1.scheme()).isEqualTo(challenge2.scheme());
        assertThat(challenge1.wwwAuthenticate()).isEqualTo(challenge2.wwwAuthenticate());
        assertThat(challenge1.details()).isEqualTo(challenge2.details());
        assertThat(challenge1).isEqualTo(challenge2);
    }

    @Test
    void l402_detailsDoNotContainFeeFields() {
        PaymentChallenge challenge = PaymentChallenge.l402(MACAROON, INVOICE, PAYMENT_HASH, AMOUNT_SATS);

        // Fee fields are domain-level, not protocol-level — must NOT appear in L402 challenge
        assertThat(challenge.details()).doesNotContainKey("platformFee");
        assertThat(challenge.details()).doesNotContainKey("verifierPayout");
        assertThat(challenge.details()).doesNotContainKey("totalAmount");
    }
}
