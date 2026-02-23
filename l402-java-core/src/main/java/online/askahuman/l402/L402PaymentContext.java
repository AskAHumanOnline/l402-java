package online.askahuman.l402;

import java.util.UUID;

/**
 * Protocol-level context for an L402 payment request.
 *
 * <p>This interface defines the minimal data the L402 protocol requires.
 * Implement it by adapting your domain's payment/request entity.</p>
 *
 * <p>Fields NOT included here (domain-specific, stay in your app):
 * platform fees, verifier payouts, risk tiers -- those are business concerns,
 * not protocol concerns.</p>
 */
public interface L402PaymentContext {

    /** Unique identifier for this payment request, stored as the {@code request_id} macaroon caveat. */
    UUID requestId();

    /** Lightning payment hash -- used as the macaroon identifier per the L402 spec. */
    String paymentHash();

    /** BOLT11 payment request included in the {@code WWW-Authenticate} header. */
    String paymentRequest();

    /** Invoice amount in satoshis, embedded as the {@code amount} macaroon caveat. */
    int amountSats();

    /** Authorization scope embedded as the {@code tier} macaroon caveat (e.g., {@code "tier_1"}). */
    String tier();
}
