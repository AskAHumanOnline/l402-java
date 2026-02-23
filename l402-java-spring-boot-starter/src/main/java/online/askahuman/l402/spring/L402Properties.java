package online.askahuman.l402.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the l402-java Spring Boot starter.
 *
 * <pre>
 * l402:
 *   secret-key: "your-secret-key"
 *   location: "https://api.example.com"
 *   expiry-seconds: 3600
 * </pre>
 */
@ConfigurationProperties("l402")
public class L402Properties {

    /** Secret key for signing macaroons. Required. */
    private String secretKey;

    /** Service location identifier embedded in macaroons. */
    private String location = "https://api.example.com";

    /** Macaroon TTL in seconds. Defaults to 3600 (1 hour). */
    private int expirySeconds = 3600;

    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public int getExpirySeconds() { return expirySeconds; }
    public void setExpirySeconds(int expirySeconds) { this.expirySeconds = expirySeconds; }
}
