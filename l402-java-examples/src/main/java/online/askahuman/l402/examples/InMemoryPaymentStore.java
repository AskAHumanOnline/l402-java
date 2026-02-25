package online.askahuman.l402.examples;

import online.askahuman.l402.L402PaymentContext;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for payment contexts.
 *
 * <p><strong>FOR DEMONSTRATION ONLY.</strong> This implementation has no eviction policy and will
 * grow unboundedly under load (each unauthenticated request adds an entry). A production
 * implementation should use a database table or cache (e.g. Redis) with TTL-based expiry keyed
 * on the macaroon {@code expirySeconds}, and should also enforce a maximum store size to prevent
 * memory exhaustion.</p>
 */
@Component
public class InMemoryPaymentStore {

    private final ConcurrentHashMap<UUID, L402PaymentContext> store = new ConcurrentHashMap<>();

    /**
     * Register a payment context so the L402 filter can later look it up.
     */
    public void register(L402PaymentContext context) {
        store.put(context.requestId(), context);
    }

    /**
     * Load a payment context by request ID.
     *
     * @throws IllegalArgumentException if not found
     */
    public L402PaymentContext load(UUID requestId) {
        L402PaymentContext ctx = store.get(requestId);
        if (ctx == null) {
            throw new IllegalArgumentException("No payment context found for request: " + requestId);
        }
        return ctx;
    }
}
