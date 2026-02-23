package online.askahuman.l402;

/**
 * Payment status for an L402 request.
 *
 * @param paid    whether payment has been confirmed
 * @param scheme  payment scheme used (e.g., {@code "L402"})
 * @param details scheme-specific status details
 */
public record PaymentStatus(
        boolean paid,
        String scheme,
        String details
) {
    /** Create a paid status. */
    public static PaymentStatus paid(String scheme) {
        return new PaymentStatus(true, scheme, "Payment confirmed");
    }

    /** Create an unpaid status with a reason. */
    public static PaymentStatus unpaid(String scheme, String reason) {
        return new PaymentStatus(false, scheme, reason);
    }
}
