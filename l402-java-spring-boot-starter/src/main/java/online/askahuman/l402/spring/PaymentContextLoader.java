package online.askahuman.l402.spring;

import online.askahuman.l402.L402PaymentContext;

import java.util.UUID;

/**
 * Loads a payment context by request ID.
 *
 * <p>Implement this functional interface to bridge the L402 filter with your application's
 * payment/request storage.</p>
 *
 * <p>Example bean definition:</p>
 * <pre>
 * {@code
 * @Bean
 * public PaymentContextLoader paymentContextLoader(MyPaymentRepository repo) {
 *     return id -> repo.findById(id).map(MyPaymentContextAdapter::new).orElseThrow();
 * }
 * }
 * </pre>
 *
 * <p><strong>Security note:</strong> {@link #load} is called by {@link L402AuthFilter} with a
 * request ID extracted from the incoming macaroon <em>before</em> cryptographic signature
 * verification. The UUID is attacker-controlled. Implementations must use lightweight lookups
 * (primary-key index, in-memory map) rather than expensive operations such as full-text search
 * or external service calls, to prevent pre-authentication denial-of-service via crafted
 * macaroons with arbitrary UUIDs.</p>
 */
@FunctionalInterface
public interface PaymentContextLoader {

    /**
     * Load the payment context for the given request ID.
     *
     * <p><strong>Important:</strong> this method is called with an unverified, attacker-supplied
     * UUID. Implementations should use simple, bounded lookups only.</p>
     *
     * @param requestId the request UUID extracted from the macaroon (not yet signature-verified)
     * @return the payment context; must not return {@code null}
     * @throws RuntimeException if the request is not found (filter will clear SecurityContext)
     */
    L402PaymentContext load(UUID requestId);
}
