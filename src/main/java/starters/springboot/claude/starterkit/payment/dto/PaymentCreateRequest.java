package starters.springboot.claude.starterkit.payment.dto;

import jakarta.validation.constraints.NotNull;
import starters.springboot.claude.starterkit.payment.domain.PaymentMethod;

public record PaymentCreateRequest(
        @NotNull Long reservationId,
        @NotNull PaymentMethod method,
        boolean forceFail
) {
}
