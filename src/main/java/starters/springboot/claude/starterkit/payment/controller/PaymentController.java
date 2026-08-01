package starters.springboot.claude.starterkit.payment.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import starters.springboot.claude.starterkit.common.response.ApiResponse;
import starters.springboot.claude.starterkit.payment.dto.PaymentCreateRequest;
import starters.springboot.claude.starterkit.payment.service.PaymentCommand;
import starters.springboot.claude.starterkit.payment.service.PaymentResult;
import starters.springboot.claude.starterkit.payment.service.PaymentService;

@Tag(name = "Payment")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ApiResponse<PaymentResult> pay(@Valid @RequestBody PaymentCreateRequest request) {
        PaymentCommand command = new PaymentCommand(request.reservationId(), request.method(), request.forceFail());
        return ApiResponse.success(paymentService.pay(command));
    }
}
