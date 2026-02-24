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
     * @throws IllegalArgumentException if {@code invoice} contains characters that would corrupt
     *                                  the {@code WWW-Authenticate} header (control characters,
     *                                  double-quotes, or backslashes)
     */
    public static PaymentChallenge l402(String macaroon, String invoice, String paymentHash, int amountSats) {
        validateInvoice(invoice);
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
     * Validates that the invoice string is safe to embed in an HTTP header value.
     *
     * <p>Rejects control characters (including CRLF), double-quotes, backslashes, and non-ASCII
     * characters to prevent HTTP response header injection. BOLT11 invoices use bech32 encoding
     * and contain only printable ASCII alphanumeric characters, so valid invoices always pass.</p>
     */
    private static void validateInvoice(String invoice) {
        if (invoice == null || invoice.isBlank()) {
            throw new IllegalArgumentException("invoice must not be null or blank");
        }
        for (int i = 0; i < invoice.length(); i++) {
            char c = invoice.charAt(i);
            if (c < 0x20 || c == '"' || c == '\\' || c > 0x7E) {
                throw new IllegalArgumentException(
                        "invoice contains invalid character at index " + i
                        + " (0x" + Integer.toHexString(c) + "): "
                        + "CRLF, double-quotes, and non-ASCII characters are not permitted");
            }
        }
    }
}
