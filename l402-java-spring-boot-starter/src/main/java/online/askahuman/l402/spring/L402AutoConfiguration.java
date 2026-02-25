package online.askahuman.l402.spring;

import online.askahuman.l402.MacaroonConfig;
import online.askahuman.l402.MacaroonService;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for the l402-java library.
 *
 * <p>Activates when {@code l402.secret-key} is set in application properties and the application
 * is a servlet-based web application. Creates {@link MacaroonConfig} and
 * {@link MacaroonService} beans.</p>
 *
 * <p>{@link online.askahuman.l402.L402Service} and {@link L402AuthFilter} are NOT
 * auto-created -- they require {@link online.askahuman.l402.LightningClient} and
 * {@link PaymentContextLoader} beans provided by the application.</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(L402Properties.class)
@ConditionalOnProperty(prefix = "l402", name = "secret-key")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class L402AutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MacaroonConfig macaroonConfig(L402Properties props) {
        if (props.getSecretKey() == null || props.getSecretKey().isBlank()) {
            throw new BeanCreationException(
                    "l402.secret-key must not be empty — set a strong random key of at least 32 characters");
        }
        if (props.getLocation() == null || props.getLocation().isBlank()) {
            throw new BeanCreationException(
                    "l402.location must not be empty — set the service URL (e.g. https://api.example.com)");
        }
        return new MacaroonConfig(props.getSecretKey(), props.getLocation(), props.getExpirySeconds());
    }

    @Bean
    @ConditionalOnMissingBean
    public MacaroonService macaroonService(MacaroonConfig config) {
        return new MacaroonService(config);
    }
}
