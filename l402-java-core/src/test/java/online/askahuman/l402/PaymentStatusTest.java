package online.askahuman.l402;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentStatusTest {

    @Test
    void paid_shouldCreateCorrectStatus() {
        PaymentStatus status = PaymentStatus.paid("L402");

        assertThat(status.paid()).isTrue();
        assertThat(status.scheme()).isEqualTo("L402");
        assertThat(status.details()).isEqualTo("Payment confirmed");
    }

    @Test
    void unpaid_shouldCreateCorrectStatus() {
        PaymentStatus status = PaymentStatus.unpaid("L402", "Invoice not yet paid");

        assertThat(status.paid()).isFalse();
        assertThat(status.scheme()).isEqualTo("L402");
        assertThat(status.details()).isEqualTo("Invoice not yet paid");
    }

    @Test
    void recordComponents_shouldBeAccessible() {
        PaymentStatus status = new PaymentStatus(true, "CUSTOM", "Custom detail");

        assertThat(status.paid()).isTrue();
        assertThat(status.scheme()).isEqualTo("CUSTOM");
        assertThat(status.details()).isEqualTo("Custom detail");
    }

    @Test
    void equality_shouldWorkCorrectly() {
        PaymentStatus paid1 = PaymentStatus.paid("L402");
        PaymentStatus paid2 = PaymentStatus.paid("L402");
        PaymentStatus unpaid = PaymentStatus.unpaid("L402", "Invoice not yet paid");

        // Records use structural equality
        assertThat(paid1).isEqualTo(paid2);
        assertThat(paid1).isNotEqualTo(unpaid);
        assertThat(paid1.hashCode()).isEqualTo(paid2.hashCode());
    }

    @Test
    void unpaid_differentReasons_shouldNotBeEqual() {
        PaymentStatus unpaid1 = PaymentStatus.unpaid("L402", "Invoice not yet paid");
        PaymentStatus unpaid2 = PaymentStatus.unpaid("L402", "Payment expired");

        assertThat(unpaid1).isNotEqualTo(unpaid2);
    }
}
