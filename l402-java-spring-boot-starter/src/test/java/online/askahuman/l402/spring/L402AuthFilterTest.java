package online.askahuman.l402.spring;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import online.askahuman.l402.L402PaymentContext;
import online.askahuman.l402.L402Service;
import online.askahuman.l402.MacaroonConfig;
import online.askahuman.l402.MacaroonService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class L402AuthFilterTest {

    private static final String SECRET_KEY = "test-secret-key-for-filter-test-1234567890ab";
    private static final String LOCATION = "https://askahuman.online";
    private static final String PAYMENT_HASH = "ec4916dd28fc4c10d78e287ca5d9cc51ee1ae73cbfde08c6b37324cbfaac8bc5";
    private static final String VALID_PREIMAGE = "0000000000000000000000000000000000000000000000000000000000000001";

    @Mock
    private L402Service l402Service;

    @Mock
    private PaymentContextLoader contextLoader;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private MacaroonService macaroonService;
    private L402AuthFilter filter;

    @BeforeEach
    void setUp() {
        macaroonService = new MacaroonService(new MacaroonConfig(SECRET_KEY, LOCATION, 3600));
        filter = new L402AuthFilter(l402Service, macaroonService, contextLoader);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // Helper: creates a real macaroon and returns the L402 credential string (macaroon:preimage)
    private String buildCredential(UUID requestId) {
        String macaroon = macaroonService.createMacaroon(requestId, PAYMENT_HASH, 25, "tier_1");
        return macaroon + ":" + VALID_PREIMAGE;
    }

    @Nested
    class ValidL402Credentials {

        @Test
        void shouldSetAuthenticationInSecurityContext() throws Exception {
            UUID requestId = UUID.randomUUID();
            String credential = buildCredential(requestId);
            String authHeader = "L402 " + credential;

            L402PaymentContext mockCtx = mock(L402PaymentContext.class);
            when(request.getHeader("Authorization")).thenReturn(authHeader);
            when(contextLoader.load(requestId)).thenReturn(mockCtx);
            when(l402Service.verifyCredential(mockCtx, credential)).thenReturn(true);

            filter.doFilterInternal(request, response, filterChain);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            assertThat(auth).isInstanceOf(L402PaymentAuthentication.class);
            verify(filterChain).doFilter(request, response);
        }

        @Test
        void shouldExtractCorrectRequestId() throws Exception {
            UUID requestId = UUID.randomUUID();
            String credential = buildCredential(requestId);
            String authHeader = "L402 " + credential;

            L402PaymentContext mockCtx = mock(L402PaymentContext.class);
            when(request.getHeader("Authorization")).thenReturn(authHeader);
            when(contextLoader.load(requestId)).thenReturn(mockCtx);
            when(l402Service.verifyCredential(mockCtx, credential)).thenReturn(true);

            filter.doFilterInternal(request, response, filterChain);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isInstanceOf(L402PaymentAuthentication.class);
            L402PaymentAuthentication l402Auth = (L402PaymentAuthentication) auth;
            assertThat(l402Auth.getRequestId()).isEqualTo(requestId);
        }
    }

    @Nested
    class InvalidL402Credentials {

        @Test
        void shouldClearContextWhenVerificationFails() throws Exception {
            UUID requestId = UUID.randomUUID();
            String credential = buildCredential(requestId);
            String authHeader = "L402 " + credential;

            L402PaymentContext mockCtx = mock(L402PaymentContext.class);
            when(request.getHeader("Authorization")).thenReturn(authHeader);
            when(contextLoader.load(requestId)).thenReturn(mockCtx);
            when(l402Service.verifyCredential(mockCtx, credential)).thenReturn(false);

            filter.doFilterInternal(request, response, filterChain);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNull();
            verify(filterChain).doFilter(request, response);
        }

        @Test
        void shouldClearContextWhenContextLoaderThrows() throws Exception {
            UUID requestId = UUID.randomUUID();
            String credential = buildCredential(requestId);
            String authHeader = "L402 " + credential;

            when(request.getHeader("Authorization")).thenReturn(authHeader);
            when(contextLoader.load(requestId)).thenThrow(new RuntimeException("Request not found"));

            filter.doFilterInternal(request, response, filterChain);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNull();
            verify(filterChain).doFilter(request, response);
        }

        @Test
        void shouldClearContextWhenVerifyCredentialThrows() throws Exception {
            UUID requestId = UUID.randomUUID();
            String credential = buildCredential(requestId);
            String authHeader = "L402 " + credential;

            L402PaymentContext mockCtx = mock(L402PaymentContext.class);
            when(request.getHeader("Authorization")).thenReturn(authHeader);
            when(contextLoader.load(requestId)).thenReturn(mockCtx);
            when(l402Service.verifyCredential(mockCtx, credential)).thenThrow(new RuntimeException("Verification error"));

            filter.doFilterInternal(request, response, filterChain);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNull();
            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    class MissingAuthorizationHeader {

        @Test
        void shouldNotSetAuthenticationWhenHeaderAbsent() throws Exception {
            when(request.getHeader("Authorization")).thenReturn(null);

            filter.doFilterInternal(request, response, filterChain);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNull();
            verify(filterChain).doFilter(request, response);
        }

        @Test
        void shouldStillContinueFilterChainWhenHeaderAbsent() throws Exception {
            when(request.getHeader("Authorization")).thenReturn(null);

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verify(contextLoader, never()).load(any());
        }
    }

    @Nested
    class JwtAuthorizationHeader {

        @Test
        void shouldNotProcessBearerToken() throws Exception {
            when(request.getHeader("Authorization")).thenReturn("Bearer eyJhbGciOiJSUzI1NiJ9.payload.sig");

            filter.doFilterInternal(request, response, filterChain);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNull();
            verify(filterChain).doFilter(request, response);
        }

        @Test
        void shouldContinueFilterChainForBearerToken() throws Exception {
            when(request.getHeader("Authorization")).thenReturn("Bearer eyJhbGciOiJSUzI1NiJ9.payload.sig");

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verify(contextLoader, never()).load(any());
        }
    }

    @Nested
    class MalformedL402Header {

        @Test
        void shouldHandleEmptyCredentialAfterL402Prefix() throws Exception {
            // "L402 " with empty string — extractL402Credential returns ""
            // split(":", 2)[0] = "" — macaroonService.extractRequestId("") will throw
            when(request.getHeader("Authorization")).thenReturn("L402 ");

            filter.doFilterInternal(request, response, filterChain);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNull();
            verify(filterChain).doFilter(request, response);
        }

        @Test
        void shouldHandleCredentialWithNoColon() throws Exception {
            // No colon: split(":", 2)[0] returns the full string as the "macaroon" part
            // macaroonService.extractRequestId("macaroon-only") should throw IllegalArgumentException
            // which is caught in the filter's catch block
            when(request.getHeader("Authorization")).thenReturn("L402 macaroon-only");

            filter.doFilterInternal(request, response, filterChain);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNull();
            verify(filterChain).doFilter(request, response);
        }

        @Test
        void shouldHandleGarbageMacaroonBase64() throws Exception {
            // Properly formatted "macaroon:preimage" but invalid base64 macaroon
            when(request.getHeader("Authorization")).thenReturn("L402 !!!garbage!!!:" + VALID_PREIMAGE);

            filter.doFilterInternal(request, response, filterChain);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNull();
            verify(filterChain).doFilter(request, response);
        }

        @Test
        void shouldHandleBasicSchemeHeader() throws Exception {
            // Basic scheme — not L402, so credential extraction returns null
            when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

            filter.doFilterInternal(request, response, filterChain);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNull();
            verify(filterChain).doFilter(request, response);
            verify(contextLoader, never()).load(any());
        }
    }

    @Nested
    class FilterChainExecution {

        @Test
        void shouldAlwaysCallFilterChain_noHeader() throws Exception {
            when(request.getHeader("Authorization")).thenReturn(null);

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        void shouldAlwaysCallFilterChain_validCredential() throws Exception {
            UUID requestId = UUID.randomUUID();
            String credential = buildCredential(requestId);

            L402PaymentContext mockCtx = mock(L402PaymentContext.class);
            when(request.getHeader("Authorization")).thenReturn("L402 " + credential);
            when(contextLoader.load(requestId)).thenReturn(mockCtx);
            when(l402Service.verifyCredential(mockCtx, credential)).thenReturn(true);

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        void shouldAlwaysCallFilterChain_invalidCredential() throws Exception {
            UUID requestId = UUID.randomUUID();
            String credential = buildCredential(requestId);

            L402PaymentContext mockCtx = mock(L402PaymentContext.class);
            when(request.getHeader("Authorization")).thenReturn("L402 " + credential);
            when(contextLoader.load(requestId)).thenReturn(mockCtx);
            when(l402Service.verifyCredential(mockCtx, credential)).thenReturn(false);

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    class SecurityContextManagement {

        @Test
        void shouldNotLeakAuthenticationAcrossRequests() throws Exception {
            // First request: valid credential sets authentication
            UUID requestId = UUID.randomUUID();
            String credential = buildCredential(requestId);

            L402PaymentContext mockCtx = mock(L402PaymentContext.class);
            when(request.getHeader("Authorization")).thenReturn("L402 " + credential);
            when(contextLoader.load(requestId)).thenReturn(mockCtx);
            when(l402Service.verifyCredential(mockCtx, credential)).thenReturn(true);

            filter.doFilterInternal(request, response, filterChain);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();

            // Second request: no header — SecurityContext from previous test tearDown is cleared
            // (setUp clears it; simulate a new request by clearing manually)
            SecurityContextHolder.clearContext();

            HttpServletRequest request2 = mock(HttpServletRequest.class);
            when(request2.getHeader("Authorization")).thenReturn(null);

            filter.doFilterInternal(request2, response, filterChain);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        void authenticatedToken_shouldBeMarkedAuthenticated() throws Exception {
            UUID requestId = UUID.randomUUID();
            String credential = buildCredential(requestId);

            L402PaymentContext mockCtx = mock(L402PaymentContext.class);
            when(request.getHeader("Authorization")).thenReturn("L402 " + credential);
            when(contextLoader.load(requestId)).thenReturn(mockCtx);
            when(l402Service.verifyCredential(mockCtx, credential)).thenReturn(true);

            filter.doFilterInternal(request, response, filterChain);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            assertThat(auth.isAuthenticated()).isTrue();
        }
    }
}
