package online.askahuman.l402.examples;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Runnable example demonstrating the l402-java library.
 *
 * <p>Start with: {@code mvn spring-boot:run -pl l402-java-examples}</p>
 *
 * <p>Then test the L402 flow:
 * <pre>
 * # Step 1: Get payment challenge (returns 402)
 * curl -X POST http://localhost:8080/api/example
 *
 * # Step 2: Pay the invoice (auto-paid after 5s in mock mode)
 *
 * # Step 3: Retry with L402 credentials (after obtaining macaroon:preimage)
 * curl -X POST http://localhost:8080/api/example \
 *   -H "Authorization: L402 &lt;macaroon&gt;:&lt;preimage&gt;"
 * </pre>
 * </p>
 */
@SpringBootApplication
public class ExampleApplication {
    public static void main(String[] args) {
        SpringApplication.run(ExampleApplication.class, args);
    }
}
