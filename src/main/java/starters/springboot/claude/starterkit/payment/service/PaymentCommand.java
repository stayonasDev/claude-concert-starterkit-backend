package starters.springboot.claude.starterkit.payment.service;

import starters.springboot.claude.starterkit.payment.domain.PaymentMethod;

public record PaymentCommand(Long reservationId, PaymentMethod method, boolean forceFail) {
}
