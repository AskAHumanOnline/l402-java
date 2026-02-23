package online.askahuman.l402.examples;

import online.askahuman.l402.L402PaymentContext;
import online.askahuman.l402.PaymentChallenge;
import online.askahuman.l402.L402Service;
import online.askahuman.l402.spring.L402PaymentAuthentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Example controller demonstrating the L402 payment-gated endpoint pattern.
 *
 * <p>Flow:
 * <ol>
 *   <li>Client POSTs without Authorization -- 402 with WWW-Authenticate + invoice</li>
 *   <li>Client pays the Lightning invoice</li>
 *   <li>Client retries with {@code Authorization: L402 <macaroon>:<preimage>} -- 200</li>
 * </ol>
 * </p>
 */
@RestController
@RequestMapping("/api/example")
public class ExampleProtectedController {

    private final InMemoryPaymentStore paymentStore;
    private final MockLightningClient lightningClient;
    private final L402Service l402Service;

    public ExampleProtectedController(InMemoryPaymentStore paymentStore,
                                       MockLightningClient lightningClient,
                                       L402Service l402Service) {
        this.paymentStore = paymentStore;
        this.lightningClient = lightningClient;
        this.l402Service = l402Service;
    }

    @PostMapping
    public ResponseEntity<?> handle(@RequestBody(required = false) Map<String, Object> body) {
        // Check if already authenticated via L402
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof L402PaymentAuthentication l402Auth) {
            UUID requestId = l402Auth.getRequestId();
            return ResponseEntity.ok(Map.of(
                    "status", "verified",
                    "requestId", requestId.toString(),
                    "message", "Payment verified! You may proceed."
            ));
        }

        // Not authenticated -- create a mock payment context and return 402
        UUID requestId = UUID.randomUUID();
        String uuidHex = requestId.toString().replace("-", "");  // 32 hex chars
        String mockPaymentHash = uuidHex + uuidHex;              // 64 hex chars (SHA256 hash length)
        String mockInvoice = "lnbc1000n1mock_invoice_for_" + uuidHex;

        L402PaymentContext ctx = new SimpleL402Context(requestId, mockPaymentHash, mockInvoice, 100, "tier_1");

        paymentStore.register(ctx);
        lightningClient.registerInvoice(mockPaymentHash);

        PaymentChallenge challenge = l402Service.createChallenge(ctx);

        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .header("WWW-Authenticate", challenge.wwwAuthenticate())
                .body(challenge.details());
    }
}

record SimpleL402Context(UUID requestId, String paymentHash, String paymentRequest, int amountSats, String tier)
        implements L402PaymentContext {}
