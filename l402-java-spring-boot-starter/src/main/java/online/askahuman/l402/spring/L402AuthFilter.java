package online.askahuman.l402.spring;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import online.askahuman.l402.L402PaymentContext;
import online.askahuman.l402.L402Service;
import online.askahuman.l402.MacaroonService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Spring Security filter that extracts and verifies L402 credentials from the Authorization header.
 *
 * <p>When a valid L402 credential is present, this filter creates an
 * {@link L402PaymentAuthentication} and places it in the SecurityContext.
 * The downstream controller checks for this authentication to decide whether the
 * request has been paid.</p>
 *
 * <p>This filter never blocks the request chain -- invalid or missing credentials
 * simply result in no authentication being set.</p>
 */
public class L402AuthFilter extends OncePerRequestFilter {

    private static final System.Logger log = System.getLogger(L402AuthFilter.class.getName());

    private final L402Service l402Service;
    private final MacaroonService macaroonService;
    private final PaymentContextLoader contextLoader;

    /**
     * @param l402Service    L402 protocol implementation
     * @param macaroonService macaroon parser (used to extract request ID before loading context)
     * @param contextLoader  loads the payment context by request ID
     */
    public L402AuthFilter(L402Service l402Service, MacaroonService macaroonService, PaymentContextLoader contextLoader) {
        this.l402Service = l402Service;
        this.macaroonService = macaroonService;
        this.contextLoader = contextLoader;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String credential = extractL402Credential(request);

        if (credential != null) {
            try {
                String macaroonBase64 = credential.split(":", 2)[0];
                UUID requestId = macaroonService.extractRequestId(macaroonBase64);

                L402PaymentContext context = contextLoader.load(requestId);
                boolean valid = l402Service.verifyCredential(context, credential);

                if (valid) {
                    L402PaymentAuthentication authentication =
                            new L402PaymentAuthentication(requestId, credential);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.log(System.Logger.Level.DEBUG, "L402 authentication set for request {0}", requestId);
                } else {
                    log.log(System.Logger.Level.DEBUG, "L402 credential verification failed for request {0}", requestId);
                    SecurityContextHolder.clearContext();
                }
            } catch (Exception e) {
                log.log(System.Logger.Level.DEBUG, "L402 authentication failed: {0}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractL402Credential(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("L402 ")) {
            return header.substring(5);
        }
        return null;
    }
}
