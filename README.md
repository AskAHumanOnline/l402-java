# l402-java

Java library for the L402 protocol — HTTP 402 + Lightning Network payments with macaroon-based authentication.

Add Lightning payment-gated endpoints to any Java application with a single Maven dependency and a few lines of configuration.

---

## Modules

| Module | Description |
|--------|-------------|
| `l402-java-core` | Pure Java — L402 protocol logic, macaroon creation/verification, no Spring |
| `l402-java-spring-boot-starter` | Spring Boot autoconfiguration — auto-creates `MacaroonService`, provides `L402AuthFilter` |
| `l402-java-examples` | Runnable Spring Boot demo — complete L402 flow without a real LND node |

---

## Maven Dependency

```xml
<!-- Core only (no Spring) -->
<dependency>
    <groupId>online.askahuman</groupId>
    <artifactId>l402-java-core</artifactId>
    <version>0.1.0</version>
</dependency>

<!-- Spring Boot starter (includes core) -->
<dependency>
    <groupId>online.askahuman</groupId>
    <artifactId>l402-java-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

---

## Quickstart (Spring Boot)

### 1. Add configuration

```yaml
l402:
  secret-key: "${L402_SECRET_KEY}"   # strong random key, 32+ chars — never commit the real value
  location: "https://api.example.com"
  expiry-seconds: 3600
```

Generate a key: `openssl rand -hex 32`

### 2. Provide the required beans

The starter auto-creates `MacaroonService`. You must provide two beans:

```java
@Configuration
public class L402Config {

    /** Tells the filter how to load your payment context by request ID. */
    @Bean
    public PaymentContextLoader paymentContextLoader(MyPaymentRepository repo) {
        return id -> repo.findById(id)
            .map(r -> (L402PaymentContext) new MyPaymentContextAdapter(r))
            .orElseThrow(() -> new NoSuchElementException("Payment not found: " + id));
    }

    /** Tells the library how to check if a Lightning invoice is paid. */
    @Bean
    public LightningClient lightningClient(MyLightningService lnd) {
        return lnd::isInvoicePaid;
    }

    /** Wire L402Service and register the filter with Spring Security. */
    @Bean
    public L402Service l402Service(MacaroonService macaroonService, LightningClient lightningClient) {
        return new L402Service(macaroonService, lightningClient);
    }

    @Bean
    public L402AuthFilter l402AuthFilter(L402Service l402Service, MacaroonService macaroonService,
                                          PaymentContextLoader contextLoader) {
        return new L402AuthFilter(l402Service, macaroonService, contextLoader);
    }

    @Bean
    public FilterRegistrationBean<L402AuthFilter> l402FilterRegistration(L402AuthFilter filter) {
        var reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false); // managed by Spring Security, not the servlet container
        return reg;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, L402AuthFilter l402AuthFilter) throws Exception {
        return http
            // CSRF protection is not required for Authorization header-based auth schemes:
            // browsers cannot set the Authorization header in cross-site requests.
            .csrf(csrf -> csrf.disable())
            .addFilterBefore(l402AuthFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
```

### 3. Implement `L402PaymentContext`

Wrap your domain's payment/request entity:

```java
record MyPaymentContext(
    UUID requestId,
    String paymentHash,
    String paymentRequest,
    int amountSats,
    String tier
) implements L402PaymentContext {}
```

### 4. Gate your endpoints

```java
@PostMapping("/api/task")
public ResponseEntity<?> handleTask() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (!(auth instanceof L402PaymentAuthentication)) {
        // Create invoice + macaroon and return 402
        var ctx = createPaymentContext();
        var challenge = l402Service.createChallenge(ctx);
        return ResponseEntity.status(402)
            .header("WWW-Authenticate", challenge.wwwAuthenticate())
            .body(challenge.details());
    }
    // Payment verified — process the request
    UUID requestId = ((L402PaymentAuthentication) auth).getRequestId();
    return ResponseEntity.ok(processRequest(requestId));
}
```

---

## L402 Flow

```
Client                          Server
  │                                │
  │── POST /api/task ─────────────>│
  │<─ 402 WWW-Authenticate: ───────│
  │   L402 macaroon="...",         │
  │       invoice="lnbc..."        │
  │                                │
  │  [client pays Lightning        │
  │   invoice, receives preimage]  │
  │                                │
  │── POST /api/task ─────────────>│
  │   Authorization: L402          │
  │   <macaroon>:<preimage>        │
  │                                │
  │<─ 200 OK ──────────────────────│
```

---

## Running the Example

```bash
# Clone and run the self-contained demo (no LND node required)
git clone https://github.com/AskAHumanOnline/l402-java.git
cd l402-java
mvn spring-boot:run -pl l402-java-examples
```

Then test the flow:

```bash
# Step 1: Hit the endpoint — get a 402 with a mock invoice
curl -s -X POST http://localhost:8080/api/example | jq .

# Step 2: Wait 5 seconds (mock client auto-pays the invoice)

# Step 3: Retry with the macaroon from the 402 response
# (In a real app the client would obtain the preimage by paying the invoice)
curl -s -X POST http://localhost:8080/api/example \
  -H "Authorization: L402 <macaroon>:<preimage>"
```

See [`l402-java-examples/`](l402-java-examples/) for the full working demo.

---

## Wallet Compatibility

Tested with:
- **WoS (Wallet of Satoshi)** — ✅ verified in production
- **Aqua** — ✅ verified in production

---

## Security Notes

- The `secretKey` is used as an HMAC secret for macaroon signing. **Never log it, never commit it to source control.**
- All security-sensitive comparisons use `MessageDigest.isEqual` (constant-time, raw bytes).
- Macaroons bind the L402 identifier to the Lightning payment hash — a macaroon cannot be reused across invoices.
- `L402AuthFilter` caps the `Authorization` header at 8192 bytes before parsing.
- The `invoice` value in the `WWW-Authenticate` header is validated to reject CRLF and control characters (header injection prevention).

### Replay Protection

By default, a valid `macaroon:preimage` credential can be reused within the macaroon's TTL window. To prevent replay attacks, wire a [`CredentialStore`](l402-java-core/src/main/java/online/askahuman/l402/CredentialStore.java) backed by a distributed cache with TTL matching `expiry-seconds`:

```java
@Bean
public CredentialStore credentialStore(StringRedisTemplate redis, L402Properties props) {
    return requestId -> {
        String key = "l402:used:" + requestId;
        Boolean firstUse = redis.opsForValue()
            .setIfAbsent(key, "1", Duration.ofSeconds(props.getExpirySeconds()));
        return Boolean.TRUE.equals(firstUse);
    };
}

@Bean
public L402Service l402Service(MacaroonService macaroonService,
                                LightningClient lightningClient,
                                CredentialStore credentialStore) {
    return new L402Service(macaroonService, lightningClient, credentialStore);
}
```

Without a `CredentialStore`, each request is verified by cryptographic means only. The two-argument `L402Service(MacaroonService, LightningClient)` constructor is provided for convenience but is not recommended for production endpoints where replaying the same payment proof is a concern.

---

## Maven Central

Publishing to Maven Central is pending GPG key setup. Until then, build locally:

```bash
mvn clean install
```

---

## License

Apache License 2.0 — see [LICENSE](LICENSE).
