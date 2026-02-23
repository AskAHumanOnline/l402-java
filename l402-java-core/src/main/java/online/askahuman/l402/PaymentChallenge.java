package online.askahuman.l402;

import java.util.Map;

/**
 * Payment challenge details returned by an L402 service.
 * Contains the {@code WWW-Authenticate} header value and scheme-specific payment details.
 *
 * @param scheme          payment scheme (e.g., {@code "L402"})
 * @param wwwAuthenticate value of the {@code WWW-Authenticate} response header
 * @param details         scheme-specific payment details (e.g., invoice, macaroon, amount)
 */
public record PaymentChallenge(
        String scheme,
        String wwwAuthenticate,
        Map<String, Object> details
) {
    /**
     * Create an L402 payment challenge with protocol-level fields only.
     *
     * @param macaroon    serialized macaroon (base64)
     * @param invoice     Lightning invoice payment request (BOLT11)
     * @param paymentHash payment hash for invoice verification
     * @param amountSats  invoice amount in satoshis
     */
    public static PaymentChallenge l402(String macaroon, String invoice, String paymentHash, int amountSats) {
        String wwwAuth = String.format("L402 macaroon=\"%s\", invoice=\"%s\"", macaroon, invoice);
        Map<String, Object> details = Map.of(
                "macaroon", macaroon,
                "invoice", invoice,
                "paymentHash", paymentHash,
                "amountSats", amountSats
        );
        return new PaymentChallenge("L402", wwwAuth, details);
    }

    /**
     * Create an x402 payment challenge stub (placeholder for future implementation).
     */
    public static PaymentChallenge x402Stub() {
        return new PaymentChallenge(
                "x402",
                "x402 not yet implemented",
                Map.of("status", "not_implemented")
        );
    }
}
