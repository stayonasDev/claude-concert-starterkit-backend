package starters.springboot.claude.starterkit.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import starters.springboot.claude.starterkit.common.exception.BusinessException;
import starters.springboot.claude.starterkit.common.exception.ErrorCode;
import starters.springboot.claude.starterkit.common.lock.LockStrategyType;
import starters.springboot.claude.starterkit.concert.domain.Concert;
import starters.springboot.claude.starterkit.concert.domain.Seat;
import starters.springboot.claude.starterkit.concert.domain.SeatGrade;
import starters.springboot.claude.starterkit.concert.domain.SeatStatus;
import starters.springboot.claude.starterkit.concert.repository.ConcertRepository;
import starters.springboot.claude.starterkit.concert.repository.SeatGradeRepository;
import starters.springboot.claude.starterkit.concert.repository.SeatRepository;
import starters.springboot.claude.starterkit.reservation.domain.ReservationStatus;
import starters.springboot.claude.starterkit.reservation.repository.ReservationRepository;
import starters.springboot.claude.starterkit.support.ContainerTestSupport;

/**
 * docs/use-cases.md UC-06 대응.
 *
 * 이 테스트는 단일 스레드로 순차 검증하므로 클래스 전체를 @Transactional로 감싸
 * 테스트마다 자동 롤백되도록 한다 (테스트 간 데이터 격리 목적). 실제 동시성을
 * 검증하는 RedisLockSeatHoldStrategyTest/PessimisticLockSeatHoldStrategyTest는
 * 여러 스레드/커넥션의 실제 커밋 가시성이 핵심이므로 이 어노테이션을 사용하지 않는다.
 */
@Transactional
class ReservationFacadeTest extends ContainerTestSupport {

    @Autowired
    private ReservationFacade reservationFacade;

    @Autowired
    private ConcertRepository concertRepository;

    @Autowired
    private SeatGradeRepository seatGradeRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    private Long openConcertId;
    private Long upcomingConcertId;
    private List<Long> openConcertSeatIds;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();

        Concert openConcert = concertRepository.save(Concert.builder()
                .title("2026 World Tour")
                .venue("잠실 올림픽 주경기장")
                .performanceAt(now.plusDays(30))
                .bookingOpenAt(now.minusDays(1))
                .bookingCloseAt(now.plusDays(29))
                .build());
        this.openConcertId = openConcert.getId();

        SeatGrade seatGrade = seatGradeRepository.save(SeatGrade.builder()
                .concertId(openConcert.getId())
                .gradeName("VIP")
                .price(BigDecimal.valueOf(200000))
                .totalCount(10)
                .build());

        this.openConcertSeatIds = java.util.stream.IntStream.rangeClosed(1, 6)
                .mapToObj(i -> seatRepository.save(Seat.builder()
                        .concertId(openConcert.getId())
                        .seatGradeId(seatGrade.getId())
                        .seatNumber("A-" + i)
                        .build()).getId())
                .toList();

        Concert upcomingConcert = concertRepository.save(Concert.builder()
                .title("2026 Winter Tour")
                .venue("고척 스카이돔")
                .performanceAt(now.plusDays(60))
                .bookingOpenAt(now.plusDays(10))
                .bookingCloseAt(now.plusDays(59))
                .build());
        this.upcomingConcertId = upcomingConcert.getId();
    }

    @Test
    void Redis_전략으로_여러_좌석을_정상적으로_예약한다() {
        ReserveSeatsCommand command = new ReserveSeatsCommand(
                1L, openConcertId, openConcertSeatIds.subList(0, 2), LockStrategyType.REDIS);

        ReservationResult result = reservationFacade.reserve(command);

        assertThat(result.status()).isEqualTo(ReservationStatus.HOLDING);
        assertThat(result.lockStrategy()).isEqualTo(LockStrategyType.REDIS);
        assertThat(seatIdsOf(result)).containsExactlyInAnyOrderElementsOf(openConcertSeatIds.subList(0, 2));

        for (Long seatId : openConcertSeatIds.subList(0, 2)) {
            Seat seat = seatRepository.findById(seatId).orElseThrow();
            assertThat(seat.getStatus()).isEqualTo(SeatStatus.HELD);
        }
    }

    @Test
    void Pessimistic_전략으로_여러_좌석을_정상적으로_예약한다() {
        ReserveSeatsCommand command = new ReserveSeatsCommand(
                1L, openConcertId, openConcertSeatIds.subList(0, 2), LockStrategyType.PESSIMISTIC);

        ReservationResult result = reservationFacade.reserve(command);

        assertThat(result.lockStrategy()).isEqualTo(LockStrategyType.PESSIMISTIC);
        assertThat(seatIdsOf(result)).containsExactlyInAnyOrderElementsOf(openConcertSeatIds.subList(0, 2));
    }

    @Test
    void 예매_오픈_전이면_예외가_발생한다() {
        ReserveSeatsCommand command = new ReserveSeatsCommand(
                1L, upcomingConcertId, List.of(1L), LockStrategyType.REDIS);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> reservationFacade.reserve(command));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BOOKING_NOT_OPEN);
    }

    @Test
    void 좌석_수가_4매를_초과하면_예외가_발생한다() {
        ReserveSeatsCommand command = new ReserveSeatsCommand(
                1L, openConcertId, openConcertSeatIds.subList(0, 5), LockStrategyType.REDIS);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> reservationFacade.reserve(command));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SEAT_LIMIT_EXCEEDED);
    }

    @Test
    void 이미_활성_예약이_있으면_추가_예약이_거부된다() {
        reservationFacade.reserve(new ReserveSeatsCommand(
                1L, openConcertId, openConcertSeatIds.subList(0, 1), LockStrategyType.REDIS));

        ReserveSeatsCommand secondCommand = new ReserveSeatsCommand(
                1L, openConcertId, openConcertSeatIds.subList(1, 2), LockStrategyType.REDIS);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> reservationFacade.reserve(secondCommand));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ACTIVE_RESERVATION_EXISTS);
    }

    // 여러 좌석 중 일부만 선점 성공한 뒤 나머지에서 실패하면, 먼저 성공한 좌석도 반환되어야 한다.
    @Test
    void 여러_좌석_중_하나가_이미_선점되어_있으면_먼저_선점된_좌석도_되돌려진다() {
        Long firstSeatId = openConcertSeatIds.get(0);
        Long alreadyHeldSeatId = openConcertSeatIds.get(1);

        // 다른 사용자가 두 번째 좌석을 미리 선점해둔 상태를 만든다.
        reservationFacade.reserve(new ReserveSeatsCommand(
                99L, openConcertId, List.of(alreadyHeldSeatId), LockStrategyType.REDIS));

        ReserveSeatsCommand command = new ReserveSeatsCommand(
                1L, openConcertId, List.of(firstSeatId, alreadyHeldSeatId), LockStrategyType.REDIS);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> reservationFacade.reserve(command));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SEAT_ALREADY_HELD);

        Seat firstSeat = seatRepository.findById(firstSeatId).orElseThrow();
        assertThat(firstSeat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);

        assertThat(reservationRepository.findByUserId(1L)).isEmpty();
    }

    private static List<Long> seatIdsOf(ReservationResult result) {
        return result.seats().stream().map(ReservationResult.SeatLine::seatId).toList();
    }
}
