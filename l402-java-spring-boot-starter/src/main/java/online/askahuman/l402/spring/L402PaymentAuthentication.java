package online.askahuman.l402.spring;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.Collections;
import java.util.UUID;

/**
 * Authentication token representing a verified L402 payment credential.
 * Set in the {@code SecurityContext} by {@link L402AuthFilter} when a valid
 * {@code Authorization: L402 ...} header is present.
 *
 * <p>Principal is the request ID (UUID); credentials is the raw
 * {@code "macaroon:preimage"} string from the Authorization header.</p>
 */
public class L402PaymentAuthentication extends AbstractAuthenticationToken {

    private final UUID requestId;
    private String credential;

    public L402PaymentAuthentication(UUID requestId, String credential) {
        super(Collections.emptyList());
        this.requestId = requestId;
        this.credential = credential;
        setAuthenticated(true);
    }

    @Override
    public Object getPrincipal() {
        return requestId;
    }

    @Override
    public Object getCredentials() {
        return credential;
    }

    @Override
    public void eraseCredentials() {
        super.eraseCredentials();
        this.credential = null;
    }

    /** Returns the request ID this authentication is for. */
    public UUID getRequestId() {
        return requestId;
    }
}
