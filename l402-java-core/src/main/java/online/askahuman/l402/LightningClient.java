package online.askahuman.l402;

/**
 * Minimal Lightning Network client interface required by the L402 protocol.
 *
 * <p>Implement this interface by delegating to your LND/CLN node or mock client.</p>
 */
@FunctionalInterface
public interface LightningClient {

    /**
     * Check whether the invoice identified by the given payment hash has been paid.
     *
     * @param paymentHash hex-encoded SHA256 payment hash
     * @return {@code true} if the invoice has been settled
     */
    boolean isInvoicePaid(String paymentHash);
}
