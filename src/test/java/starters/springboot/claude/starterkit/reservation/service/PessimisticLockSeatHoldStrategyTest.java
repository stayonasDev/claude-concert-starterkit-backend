package starters.springboot.claude.starterkit.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import starters.springboot.claude.starterkit.support.ContainerTestSupport;

/**
 * docs/test-scenarios.md TS-RESV-02, TS-RESV-04 대응.
 */
class PessimisticLockSeatHoldStrategyTest extends ContainerTestSupport {

    @Autowired
    private PessimisticLockSeatHoldStrategy pessimisticLockSeatHoldStrategy;

    @Autowired
    private ConcertRepository concertRepository;

    @Autowired
    private SeatGradeRepository seatGradeRepository;

    @Autowired
    private SeatRepository seatRepository;

    private Long seatId;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();
        Concert concert = concertRepository.save(Concert.builder()
                .title("2026 World Tour")
                .venue("잠실 올림픽 주경기장")
                .performanceAt(now.plusDays(30))
                .bookingOpenAt(now.minusDays(1))
                .bookingCloseAt(now.plusDays(29))
                .build());

        SeatGrade seatGrade = seatGradeRepository.save(SeatGrade.builder()
                .concertId(concert.getId())
                .gradeName("VIP")
                .price(BigDecimal.valueOf(200000))
                .totalCount(100)
                .build());

        Seat seat = seatRepository.save(Seat.builder()
                .concertId(concert.getId())
                .seatGradeId(seatGrade.getId())
                .seatNumber("A-1")
                .build());

        this.seatId = seat.getId();
    }

    @Test
    void 정상적으로_AVAILABLE_좌석을_선점한다() {
        LocalDateTime holdExpiresAt = LocalDateTime.now().plusMinutes(5);

        SeatHoldResult result = pessimisticLockSeatHoldStrategy.hold(seatId, 1L, holdExpiresAt);

        assertThat(result.lockStrategy()).isEqualTo(LockStrategyType.PESSIMISTIC);
        Seat seat = seatRepository.findById(seatId).orElseThrow();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(seat.getHeldByUserId()).isEqualTo(1L);
    }

    @Test
    void 이미_HELD된_좌석을_선점하면_예외가_발생한다() {
        LocalDateTime holdExpiresAt = LocalDateTime.now().plusMinutes(5);
        pessimisticLockSeatHoldStrategy.hold(seatId, 1L, holdExpiresAt);

        BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class,
                () -> pessimisticLockSeatHoldStrategy.hold(seatId, 2L, holdExpiresAt)
        );
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SEAT_ALREADY_HELD);
    }

    // TS-RESV-04: 동일 좌석에 대한 100개 동시 요청 중 정확히 1건만 성공해야 한다 (FR-11, NFR-01)
    @Test
    void 동시에_100개_요청이_들어와도_정확히_한_건만_선점에_성공한다() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            long userId = i + 1L;
            executor.submit(() -> {
                try {
                    pessimisticLockSeatHoldStrategy.hold(seatId, userId, LocalDateTime.now().plusMinutes(5));
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(threadCount - 1);

        Seat seat = seatRepository.findById(seatId).orElseThrow();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.HELD);
    }
}
