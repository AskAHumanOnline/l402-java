package online.askahuman.l402.spring;

import online.askahuman.l402.MacaroonConfig;
import online.askahuman.l402.MacaroonService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class L402AutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(L402AutoConfiguration.class));

    @Test
    void createsBeansWhenSecretKeyAndLocationConfigured() {
        contextRunner
                .withPropertyValues(
                        "l402.secret-key=test-secret-key-for-autoconfig-test-12345678",
                        "l402.location=https://api.example.com"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(MacaroonConfig.class);
                    assertThat(context).hasSingleBean(MacaroonService.class);
                });
    }

    @Test
    void doesNotActivateWhenSecretKeyPropertyAbsent() {
        contextRunner
                .withPropertyValues("l402.location=https://api.example.com")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MacaroonConfig.class);
                    assertThat(context).doesNotHaveBean(MacaroonService.class);
                });
    }

    @Test
    void failsWithClearMessageWhenSecretKeyIsBlank() {
        contextRunner
                .withPropertyValues(
                        "l402.secret-key=",
                        "l402.location=https://api.example.com"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure().getMessage())
                            .contains("l402.secret-key must not be empty");
                });
    }

    @Test
    void failsWithClearMessageWhenLocationIsBlank() {
        contextRunner
                .withPropertyValues(
                        "l402.secret-key=test-secret-key-for-autoconfig-test-12345678",
                        "l402.location="
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure().getMessage())
                            .contains("l402.location must not be empty");
                });
    }

    @Test
    void usesDefaultExpirySeconds() {
        contextRunner
                .withPropertyValues(
                        "l402.secret-key=test-secret-key-for-autoconfig-test-12345678",
                        "l402.location=https://api.example.com"
                )
                .run(context -> {
                    MacaroonConfig config = context.getBean(MacaroonConfig.class);
                    assertThat(config.getExpirySeconds()).isEqualTo(3600);
                    assertThat(config.getLocation()).isEqualTo("https://api.example.com");
                });
    }

    @Test
    void respectsCustomExpirySeconds() {
        contextRunner
                .withPropertyValues(
                        "l402.secret-key=test-secret-key-for-autoconfig-test-12345678",
                        "l402.location=https://api.example.com",
                        "l402.expiry-seconds=7200"
                )
                .run(context -> {
                    MacaroonConfig config = context.getBean(MacaroonConfig.class);
                    assertThat(config.getExpirySeconds()).isEqualTo(7200);
                });
    }

    @Test
    void doesNotOverrideUserProvidedMacaroonConfig() {
        MacaroonConfig customConfig = new MacaroonConfig(
                "custom-secret-key-that-is-at-least-32-chars-long", "https://custom.example.com", 1800);
        contextRunner
                .withPropertyValues(
                        "l402.secret-key=test-secret-key-for-autoconfig-test-12345678",
                        "l402.location=https://api.example.com"
                )
                .withBean(MacaroonConfig.class, () -> customConfig)
                .run(context -> {
                    assertThat(context).hasSingleBean(MacaroonConfig.class);
                    assertThat(context.getBean(MacaroonConfig.class)).isSameAs(customConfig);
                });
    }

    @Test
    void doesNotOverrideUserProvidedMacaroonService() {
        MacaroonService customService = new MacaroonService(
                new MacaroonConfig("custom-secret-key-that-is-at-least-32-chars-long",
                        "https://custom.example.com", 1800));
        contextRunner
                .withPropertyValues(
                        "l402.secret-key=test-secret-key-for-autoconfig-test-12345678",
                        "l402.location=https://api.example.com"
                )
                .withBean(MacaroonService.class, () -> customService)
                .run(context -> {
                    assertThat(context).hasSingleBean(MacaroonService.class);
                    assertThat(context.getBean(MacaroonService.class)).isSameAs(customService);
                });
    }
}
