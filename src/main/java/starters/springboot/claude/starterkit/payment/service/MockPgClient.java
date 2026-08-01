package starters.springboot.claude.starterkit.payment.service;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 실제 PG 연동 없이 결제 성공/실패를 흉내낸다 (docs/requirements.md 제약사항:
 * "100% 성공/실패를 임의 제어 가능하게 설계"). forceFail은 클라이언트가 실패 시나리오를
 * 재현/테스트할 수 있도록 노출한 파라미터로, 실제 PG 연동 시에는 사라질 목적의 필드다.
 */
@Component
public class MockPgClient {

    public PgChargeResult charge(BigDecimal amount, boolean forceFail) {
        if (forceFail) {
            return PgChargeResult.failure();
        }
        return PgChargeResult.success("mock-tx-" + UUID.randomUUID());
    }

    public record PgChargeResult(boolean success, String transactionId) {

        public static PgChargeResult success(String transactionId) {
            return new PgChargeResult(true, transactionId);
        }

        public static PgChargeResult failure() {
            return new PgChargeResult(false, null);
        }
    }
}
