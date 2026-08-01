package starters.springboot.claude.starterkit.common.lock;

/**
 * 좌석 선점 동시성 제어 전략 식별자.
 * 실제 락 구현체는 reservation 도메인에 위치한다 (docs/architecture.md 3장 참고).
 */
public enum LockStrategyType {
    REDIS,
    PESSIMISTIC
}
