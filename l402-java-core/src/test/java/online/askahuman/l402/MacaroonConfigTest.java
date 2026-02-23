package online.askahuman.l402;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MacaroonConfigTest {

    private static final String VALID_SECRET = "this-is-a-valid-secret-key-1234567890ab";
    private static final String VALID_LOCATION = "https://api.example.com";

    @Test
    void validConfig_shouldCreateSuccessfully() {
        MacaroonConfig config = new MacaroonConfig(VALID_SECRET, VALID_LOCATION, 3600);
        assertThat(config).isNotNull();
    }

    @Test
    void getters_shouldReturnCorrectValues() {
        MacaroonConfig config = new MacaroonConfig(VALID_SECRET, VALID_LOCATION, 7200);
        assertThat(config.getSecretKey()).isEqualTo(VALID_SECRET);
        assertThat(config.getLocation()).isEqualTo(VALID_LOCATION);
        assertThat(config.getExpirySeconds()).isEqualTo(7200);
    }

    @Test
    void nullSecretKey_shouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> new MacaroonConfig(null, VALID_LOCATION, 3600))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("secretKey");
    }

    @Test
    void tooShortSecretKey_shouldThrowIllegalArgumentException() {
        // 31 characters — one short of the 32-char minimum
        String shortKey = "this-is-only-31-chars-long-key!";
        assertThat(shortKey.length()).isEqualTo(31);
        assertThatThrownBy(() -> new MacaroonConfig(shortKey, VALID_LOCATION, 3600))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("secretKey");
    }

    @Test
    void exactlyThirtyTwoCharSecretKey_shouldCreateSuccessfully() {
        // Exactly 32 characters — minimum allowed
        String exactly32 = "12345678901234567890123456789012";
        assertThat(exactly32.length()).isEqualTo(32);
        MacaroonConfig config = new MacaroonConfig(exactly32, VALID_LOCATION, 3600);
        assertThat(config.getSecretKey()).isEqualTo(exactly32);
    }

    @Test
    void nullLocation_shouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> new MacaroonConfig(VALID_SECRET, null, 3600))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("location");
    }

    @Test
    void blankLocation_shouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> new MacaroonConfig(VALID_SECRET, "   ", 3600))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("location");
    }

    @Test
    void emptyLocation_shouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> new MacaroonConfig(VALID_SECRET, "", 3600))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("location");
    }

    @Test
    void zeroExpirySeconds_shouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> new MacaroonConfig(VALID_SECRET, VALID_LOCATION, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expirySeconds");
    }

    @Test
    void negativeExpirySeconds_shouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> new MacaroonConfig(VALID_SECRET, VALID_LOCATION, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expirySeconds");
    }

    @Test
    void minimumPositiveExpirySeconds_shouldCreateSuccessfully() {
        MacaroonConfig config = new MacaroonConfig(VALID_SECRET, VALID_LOCATION, 1);
        assertThat(config.getExpirySeconds()).isEqualTo(1);
    }
}
