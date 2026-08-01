package starters.springboot.claude.starterkit.payment.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import starters.springboot.claude.starterkit.payment.domain.Payment;
import starters.springboot.claude.starterkit.payment.domain.PaymentStatus;

public record PaymentResult(
        Long paymentId,
        PaymentStatus status,
        BigDecimal amount,
        String pgTransactionId,
        LocalDateTime paidAt
) {

    static PaymentResult from(Payment payment) {
        return new PaymentResult(
                payment.getId(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getPgTransactionId(),
                payment.getPaidAt()
        );
    }
}
