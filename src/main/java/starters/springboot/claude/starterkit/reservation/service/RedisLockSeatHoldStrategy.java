package starters.springboot.claude.starterkit.reservation.service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import starters.springboot.claude.starterkit.common.exception.BusinessException;
import starters.springboot.claude.starterkit.common.exception.ErrorCode;
import starters.springboot.claude.starterkit.common.lock.LockStrategyType;
import starters.springboot.claude.starterkit.concert.domain.Seat;
import starters.springboot.claude.starterkit.concert.repository.SeatRepository;

/**
 * Redis 분산락(Redisson RLock) 기반 좌석 선점 전략 (docs/architecture.md 3.2절).
 *
 * 락 leaseTime(5초)은 좌석 HOLD 정책 시간(5분)과 무관하게 짧게 유지한다 — 락은
 * "요청 처리 중 잠깐의 배타성"만 보장하면 되고, 실제 HOLD 상태 유지는 DB(seats.status
 * + hold_expires_at)가 책임진다 (docs/architecture.md 6장 정합성 원칙).
 * watchdog(자동 연장)은 사용하지 않는다 — leaseTime을 고정해 락이 예상보다 오래
 * 유지되는 상황을 학습자가 쉽게 추론할 수 있게 하기 위함이다.
 */
@Component
@RequiredArgsConstructor
public class RedisLockSeatHoldStrategy implements SeatHoldStrategy {

    private static final String LOCK_KEY_PREFIX = "lock:seat:";
    private static final long WAIT_TIME_SECONDS = 3L;
    private static final long LEASE_TIME_SECONDS = 5L;

    private final RedissonClient redissonClient;
    private final SeatRepository seatRepository;

    @Override
    public SeatHoldResult hold(Long seatId, Long userId, LocalDateTime holdExpiresAt) {
        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + seatId);

        boolean acquired;
        try {
            acquired = lock.tryLock(WAIT_TIME_SECONDS, LEASE_TIME_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("좌석 락 대기 중 인터럽트가 발생했습니다. seatId=" + seatId, e);
        }

        // 대기 시간 내에 락을 얻지 못한 경우도 "지금은 선점 불가"이므로 좌석 상태 충돌과
        // 동일한 에러 코드로 응답한다 (docs/error-codes.md 참고).
        if (!acquired) {
            throw new BusinessException(ErrorCode.SEAT_ALREADY_HELD);
        }

        try {
            return updateSeat(seatId, userId, holdExpiresAt);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // findById + save를 하나의 트랜잭션으로 묶지 않는다: 배타성은 Redis 락이 보장하므로
    // DB 트랜잭션 범위를 넓혀 커넥션을 오래 점유할 필요가 없다 (docs/architecture.md 3.2절).
    private SeatHoldResult updateSeat(Long seatId, Long userId, LocalDateTime holdExpiresAt) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SEAT_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        seat.hold(userId, now, holdExpiresAt);
        seatRepository.save(seat);

        return new SeatHoldResult(seat.getId(), userId, now, holdExpiresAt, LockStrategyType.REDIS);
    }
}
