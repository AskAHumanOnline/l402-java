package online.askahuman.l402.examples;

import online.askahuman.l402.LightningClient;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock Lightning client that automatically "pays" invoices after a configurable delay.
 *
 * <p><strong>FOR DEMONSTRATION ONLY.</strong> This client reports all registered invoices as paid
 * after a fixed delay, with no real Lightning Network interaction. It must not be used in
 * production. Wire a real {@link online.askahuman.l402.LightningClient} implementation (e.g.
 * backed by LND, Core Lightning, or an LSP SDK) in any deployed application.</p>
 *
 * <p>Note: This class is annotated {@code @Component} so it auto-wires in the example application
 * context. If you copy the example module as a starting point, remove or replace this class
 * before deploying.</p>
 */
@Component
public class MockLightningClient implements LightningClient {

    private static final System.Logger log = System.getLogger(MockLightningClient.class.getName());

    private final long autoPayDelaySeconds;
    private final Map<String, Instant> invoiceCreatedAt = new ConcurrentHashMap<>();

    public MockLightningClient() {
        this(5);
    }

    public MockLightningClient(long autoPayDelaySeconds) {
        this.autoPayDelaySeconds = autoPayDelaySeconds;
    }

    /**
     * Register an invoice. The mock client will report it as paid after the configured delay.
     */
    public void registerInvoice(String paymentHash) {
        invoiceCreatedAt.put(paymentHash, Instant.now());
        log.log(System.Logger.Level.INFO, "Mock invoice registered: {0} (auto-pay after {1}s)", paymentHash, autoPayDelaySeconds);
    }

    @Override
    public boolean isInvoicePaid(String paymentHash) {
        Instant created = invoiceCreatedAt.get(paymentHash);
        if (created == null) {
            return false;
        }
        boolean paid = Instant.now().isAfter(created.plusSeconds(autoPayDelaySeconds));
        if (paid) {
            log.log(System.Logger.Level.INFO, "Mock invoice auto-paid: {0}", paymentHash);
        }
        return paid;
    }
}
