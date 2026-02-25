package online.askahuman.l402;

/**
 * Core L402 protocol implementation.
 *
 * <p>Handles challenge creation, credential verification, and payment status checks.
 * This class has no Spring dependencies -- wire it up with your DI framework of choice.</p>
 *
 * <p><strong>Replay protection:</strong> By default, verified credentials may be reused within
 * the macaroon's TTL window. To prevent replay attacks, supply a {@link CredentialStore} that
 * tracks consumed request IDs (e.g. backed by Redis with a TTL matching
 * {@link MacaroonConfig#getExpirySeconds()}). See {@link CredentialStore} for details and a
 * complete example.</p>
 */
public final class L402Service {

    private static final System.Logger log = System.getLogger(L402Service.class.getName());

    private final MacaroonService macaroonService;
    private final LightningClient lightningClient;
    private final CredentialStore credentialStore;

    /**
     * @param macaroonService configured macaroon service
     * @param lightningClient client to check invoice payment status
     * @param credentialStore store for consumed credential IDs (replay protection)
     */
    public L402Service(MacaroonService macaroonService, LightningClient lightningClient,
                       CredentialStore credentialStore) {
        this.macaroonService = macaroonService;
        this.lightningClient = lightningClient;
        this.credentialStore = credentialStore;
    }

    /**
     * Convenience constructor without replay protection.
     *
     * <p><strong>Security warning:</strong> Without a {@link CredentialStore}, a valid
     * {@code macaroon:preimage} credential can be reused within the macaroon's TTL window.
     * Use {@link #L402Service(MacaroonService, LightningClient, CredentialStore)} in production
     * environments where credential replay is a concern.</p>
     *
     * @param macaroonService configured macaroon service
     * @param lightningClient client to check invoice payment status
     */
    public L402Service(MacaroonService macaroonService, LightningClient lightningClient) {
        this(macaroonService, lightningClient, requestId -> true);
    }

    /**
     * Create an L402 payment challenge for the given context.
     *
     * @param ctx protocol-level payment context
     * @return challenge containing {@code WWW-Authenticate} header and details
     */
    public PaymentChallenge createChallenge(L402PaymentContext ctx) {
        log.log(System.Logger.Level.INFO, "Creating L402 challenge for request {0}", ctx.requestId());

        String macaroon = macaroonService.createMacaroon(
                ctx.requestId(),
                ctx.paymentHash(),
                ctx.amountSats(),
                ctx.tier()
        );

        return PaymentChallenge.l402(macaroon, ctx.paymentRequest(), ctx.paymentHash(), ctx.amountSats());
    }

    /**
     * Verify an L402 credential ({@code macaroon:preimage}) against the given context.
     *
     * @param ctx        protocol-level payment context
     * @param credential raw credential string from the {@code Authorization: L402 ...} header
     * @return {@code true} if the credential is cryptographically valid and has not been replayed
     */
    public boolean verifyCredential(L402PaymentContext ctx, String credential) {
        try {
            String[] parts = credential.split(":", 2);
            if (parts.length != 2) {
                log.log(System.Logger.Level.WARNING,
                        "Invalid L402 credential format for request {0}: missing colon separator",
                        ctx.requestId());
                return false;
            }

            String macaroonBase64 = parts[0];
            String preimageHex = parts[1];

            boolean valid = macaroonService.verifyL402(
                    macaroonBase64,
                    preimageHex,
                    ctx.paymentHash(),
                    ctx.tier(),
                    ctx.amountSats()
            );

            if (valid) {
                if (!credentialStore.consume(ctx.requestId())) {
                    log.log(System.Logger.Level.WARNING,
                            "L402 credential replay detected for request {0}", ctx.requestId());
                    return false;
                }
                log.log(System.Logger.Level.INFO, "L402 credentials verified for request {0}", ctx.requestId());
            } else {
                log.log(System.Logger.Level.WARNING, "L402 verification failed for request {0}", ctx.requestId());
            }

            return valid;

        } catch (Exception e) {
            log.log(System.Logger.Level.ERROR,
                    "L402 credential verification error for request {0}: {1}",
                    ctx.requestId(), e.getMessage());
            return false;
        }
    }

    /**
     * Check whether the Lightning invoice identified by the given payment hash has been paid.
     *
     * @param paymentHash hex-encoded payment hash
     * @return payment status
     */
    public PaymentStatus getPaymentStatus(String paymentHash) {
        boolean paid = lightningClient.isInvoicePaid(paymentHash);

        if (paid) {
            log.log(System.Logger.Level.INFO, "Lightning invoice paid for hash {0}", paymentHash);
            return PaymentStatus.paid("L402");
        } else {
            return PaymentStatus.unpaid("L402", "Invoice not yet paid");
        }
    }
}
