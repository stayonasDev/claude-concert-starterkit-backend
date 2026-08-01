package starters.springboot.claude.starterkit.reservation.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import starters.springboot.claude.starterkit.common.exception.BusinessException;
import starters.springboot.claude.starterkit.common.exception.ErrorCode;
import starters.springboot.claude.starterkit.common.lock.LockStrategyType;
import starters.springboot.claude.starterkit.concert.domain.Concert;
import starters.springboot.claude.starterkit.concert.domain.Seat;
import starters.springboot.claude.starterkit.concert.domain.SeatGrade;
import starters.springboot.claude.starterkit.concert.repository.ConcertRepository;
import starters.springboot.claude.starterkit.concert.repository.SeatGradeRepository;
import starters.springboot.claude.starterkit.concert.repository.SeatRepository;
import starters.springboot.claude.starterkit.reservation.domain.Reservation;
import starters.springboot.claude.starterkit.reservation.domain.ReservationSeat;
import starters.springboot.claude.starterkit.reservation.domain.ReservationStatus;
import starters.springboot.claude.starterkit.reservation.repository.ReservationRepository;

/**
 * 좌석 선점 → 예약 생성 오케스트레이션 (docs/architecture.md UC-06, docs/use-cases.md UC-06).
 *
 * 대기열 검증(QueueAdmissionInterceptor)은 이 클래스의 책임이 아니다 — 컨트롤러 계층에
 * 인터셉터로 배치되어 이 메서드에 도달하기 전에 이미 통과된 상태라고 가정한다.
 *
 * 의도적으로 전체 메서드를 @Transactional로 감싸지 않는다: RedisLockSeatHoldStrategy는
 * "락 해제 전에 DB 커밋이 끝나야 한다"는 전제로 설계되어 있는데(docs/architecture.md 6장),
 * 이 메서드 전체를 하나의 트랜잭션으로 묶으면 개별 hold() 호출의 커밋이 상위 트랜잭션이
 * 끝날 때까지 미뤄져 그 전제가 깨진다. 대신 각 저장 단위(Seat, Reservation)가 각자의
 * 트랜잭션 경계를 갖도록 하고, 부분 실패 시에는 이미 선점된 좌석을 명시적으로 반환한다.
 */
@Service
public class ReservationFacade {

    private static final int MAX_SEATS_PER_RESERVATION = 4;
    private static final long HOLD_DURATION_MINUTES = 5L;

    private final ConcertRepository concertRepository;
    private final SeatRepository seatRepository;
    private final SeatGradeRepository seatGradeRepository;
    private final ReservationRepository reservationRepository;
    private final Map<LockStrategyType, SeatHoldStrategy> seatHoldStrategies;

    public ReservationFacade(ConcertRepository concertRepository,
                              SeatRepository seatRepository,
                              SeatGradeRepository seatGradeRepository,
                              ReservationRepository reservationRepository,
                              RedisLockSeatHoldStrategy redisLockSeatHoldStrategy,
                              PessimisticLockSeatHoldStrategy pessimisticLockSeatHoldStrategy) {
        this.concertRepository = concertRepository;
        this.seatRepository = seatRepository;
        this.seatGradeRepository = seatGradeRepository;
        this.reservationRepository = reservationRepository;
        this.seatHoldStrategies = Map.of(
                LockStrategyType.REDIS, redisLockSeatHoldStrategy,
                LockStrategyType.PESSIMISTIC, pessimisticLockSeatHoldStrategy
        );
    }

    public ReservationResult reserve(ReserveSeatsCommand command) {
        validateSeatCount(command.seatIds());

        Concert concert = concertRepository.findById(command.concertId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONCERT_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        if (concert.isBeforeBookingOpen(now)) {
            throw new BusinessException(ErrorCode.BOOKING_NOT_OPEN);
        }
        if (concert.isAfterBookingClose(now)) {
            throw new BusinessException(ErrorCode.BOOKING_CLOSED);
        }

        validateNoActiveReservation(command.userId(), command.concertId());

        SeatHoldStrategy strategy = seatHoldStrategies.get(command.lockStrategy());
        LocalDateTime holdExpiresAt = now.plusMinutes(HOLD_DURATION_MINUTES);

        Reservation reservation = Reservation.builder()
                .userId(command.userId())
                .concertId(command.concertId())
                .lockStrategy(command.lockStrategy())
                .heldAt(now)
                .holdExpiresAt(holdExpiresAt)
                .build();

        // 데드락 방지: 여러 좌석을 동시에 선점할 때는 seatId 오름차순으로 순차 처리한다
        // (docs/architecture.md 3.3절 - PessimisticLockSeatHoldStrategy 주석 참고).
        List<Long> sortedSeatIds = command.seatIds().stream().sorted().toList();
        List<Long> heldSeatIds = new ArrayList<>();

        try {
            for (Long seatId : sortedSeatIds) {
                ReservationSeat reservationSeat = holdAndBuildLine(strategy, seatId, command.userId(), holdExpiresAt);
                heldSeatIds.add(seatId);
                reservation.addReservationSeat(reservationSeat);
            }
        } catch (BusinessException e) {
            // 이미 선점에 성공한 좌석이 있는데 이후 좌석에서 실패한 경우, 그대로 두면
            // 어떤 Reservation과도 연결되지 않은 채 영구히 HELD로 남아 만료 스케줄러
            // (Reservation 기준으로 스캔)로도 정리되지 않는다. 따라서 명시적으로 되돌린다.
            releaseSeats(heldSeatIds);
            throw e;
        }

        Reservation saved = reservationRepository.save(reservation);
        return ReservationResult.from(saved);
    }

    private ReservationSeat holdAndBuildLine(SeatHoldStrategy strategy, Long seatId, Long userId,
                                              LocalDateTime holdExpiresAt) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SEAT_NOT_FOUND));
        SeatGrade seatGrade = seatGradeRepository.findById(seat.getSeatGradeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SEAT_NOT_FOUND));

        strategy.hold(seatId, userId, holdExpiresAt);

        return ReservationSeat.builder()
                .seatId(seatId)
                .priceSnapshot(seatGrade.getPrice())
                .build();
    }

    private void releaseSeats(List<Long> seatIds) {
        for (Long seatId : seatIds) {
            seatRepository.findById(seatId).ifPresent(seat -> {
                seat.release();
                seatRepository.save(seat);
            });
        }
    }

    private void validateSeatCount(List<Long> seatIds) {
        if (seatIds.isEmpty() || seatIds.size() > MAX_SEATS_PER_RESERVATION) {
            throw new BusinessException(ErrorCode.SEAT_LIMIT_EXCEEDED);
        }
    }

    private void validateNoActiveReservation(Long userId, Long concertId) {
        boolean hasActiveReservation = reservationRepository.existsByUserIdAndConcertIdAndStatusIn(
                userId, concertId, List.of(ReservationStatus.HOLDING, ReservationStatus.PENDING_PAYMENT));
        if (hasActiveReservation) {
            throw new BusinessException(ErrorCode.ACTIVE_RESERVATION_EXISTS);
        }
    }
}
