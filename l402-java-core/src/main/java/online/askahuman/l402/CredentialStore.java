package online.askahuman.l402;

import java.util.UUID;

/**
 * Tracks consumed L402 credentials to prevent replay attacks.
 *
 * <p><strong>Security context:</strong> The L402 protocol is stateless by cryptographic design.
 * Without a {@code CredentialStore}, a valid {@code macaroon:preimage} credential can be reused
 * indefinitely within the macaroon's TTL window (controlled by
 * {@link MacaroonConfig#getExpirySeconds()}). An attacker who intercepts a credential — via
 * man-in-the-middle, log exfiltration, or a compromised client — can replay it for any subsequent
 * request within that window.</p>
 *
 * <p><strong>Wire a CredentialStore in production:</strong> Implement this interface and pass it
 * to {@link L402Service#L402Service(MacaroonService, LightningClient, CredentialStore)}. The
 * recommended backing store is Redis with a TTL matching the macaroon expiry.</p>
 *
 * <p>Example (Redis-backed implementation):</p>
 * <pre>{@code
 * @Bean
 * public CredentialStore credentialStore(StringRedisTemplate redis, L402Properties props) {
 *     return requestId -> {
 *         String key = "l402:used:" + requestId;
 *         // setIfAbsent returns true on first use (not yet consumed), false on replay
 *         Boolean firstUse = redis.opsForValue()
 *             .setIfAbsent(key, "1", Duration.ofSeconds(props.getExpirySeconds()));
 *         return Boolean.TRUE.equals(firstUse);
 *     };
 * }
 * }</pre>
 *
 * <p>When replay protection is not required (e.g. in read-only endpoints or tests), the
 * two-argument {@link L402Service#L402Service(MacaroonService, LightningClient)} constructor uses
 * a no-op store that always returns {@code true}. See that constructor's Javadoc for the
 * associated security warning.</p>
 */
@FunctionalInterface
public interface CredentialStore {

    /**
     * Attempt to mark the credential identified by {@code requestId} as consumed.
     *
     * <p>Implementations must be thread-safe. This method is called after successful
     * cryptographic verification; it is never called for invalid credentials.</p>
     *
     * @param requestId the request UUID extracted from the verified macaroon
     * @return {@code true} if this is the first use of this credential (request accepted),
     *         {@code false} if the credential has already been consumed (replay rejected)
     */
    boolean consume(UUID requestId);
}
