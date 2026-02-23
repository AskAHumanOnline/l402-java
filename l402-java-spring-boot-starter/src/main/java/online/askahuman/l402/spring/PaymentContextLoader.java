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
 */
@FunctionalInterface
public interface PaymentContextLoader {

    /**
     * Load the payment context for the given request ID.
     *
     * @param requestId the request UUID extracted from the macaroon
     * @return the payment context; must not return {@code null}
     * @throws RuntimeException if the request is not found (filter will clear SecurityContext)
     */
    L402PaymentContext load(UUID requestId);
}
