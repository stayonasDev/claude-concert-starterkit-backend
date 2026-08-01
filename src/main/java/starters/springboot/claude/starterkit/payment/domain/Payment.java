package starters.springboot.claude.starterkit.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import starters.springboot.claude.starterkit.common.domain.BaseTimeEntity;

@Entity
@Table(
        name = "payments",
        indexes = @Index(name = "idx_payments_status", columnList = "status")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // reservations : payments = 1:1 (uk_payments_reservation)
    @Column(name = "reservation_id", nullable = false, unique = true)
    private Long reservationId;

    @Column(nullable = false, precision = 10, scale = 0)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "pg_transaction_id", length = 100)
    private String pgTransactionId;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Builder
    private Payment(Long reservationId, BigDecimal amount, PaymentMethod method) {
        this.reservationId = reservationId;
        this.amount = amount;
        this.method = method;
        this.status = PaymentStatus.READY;
    }

    // FR-15
    public void markPaid(String pgTransactionId, LocalDateTime paidAt) {
        this.status = PaymentStatus.PAID;
        this.pgTransactionId = pgTransactionId;
        this.paidAt = paidAt;
    }

    // FR-16
    public void markFailed() {
        this.status = PaymentStatus.FAILED;
    }

    public void cancel() {
        this.status = PaymentStatus.CANCELLED;
    }
}
