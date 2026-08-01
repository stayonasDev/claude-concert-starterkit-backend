package starters.springboot.claude.starterkit.reservation.service;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import starters.springboot.claude.starterkit.common.exception.BusinessException;
import starters.springboot.claude.starterkit.common.exception.ErrorCode;
import starters.springboot.claude.starterkit.common.lock.LockStrategyType;
import starters.springboot.claude.starterkit.concert.domain.Seat;
import starters.springboot.claude.starterkit.concert.repository.SeatRepository;

/**
 * DB 비관적 락(SELECT ... FOR UPDATE) 기반 좌석 선점 전략 (docs/architecture.md 3.3절).
 *
 * SeatRepository.findByIdForUpdate가 획득한 행 배타 락은 트랜잭션 커밋/롤백 시점까지
 * 유지된다. seat.hold() 이후 별도의 save() 호출 없이 JPA 변경 감지(dirty checking)로
 * 커밋 시 자동 반영된다 — Redis 전략과 달리 "락의 유효 범위 = 트랜잭션 경계"이기 때문이다.
 *
 * 여러 좌석을 동시에 선점하는 경우 이 메서드를 호출하는 상위 계층이 반드시 seatId
 * 오름차순으로 정렬한 뒤 순차 호출해야 데드락을 피할 수 있다 (SeatRepository 참고).
 */
@Component
@RequiredArgsConstructor
public class PessimisticLockSeatHoldStrategy implements SeatHoldStrategy {

    private final SeatRepository seatRepository;

    @Override
    @Transactional
    public SeatHoldResult hold(Long seatId, Long userId, LocalDateTime holdExpiresAt) {
        Seat seat = seatRepository.findByIdForUpdate(seatId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SEAT_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        seat.hold(userId, now, holdExpiresAt);

        return new SeatHoldResult(seat.getId(), userId, now, holdExpiresAt, LockStrategyType.PESSIMISTIC);
    }
}
