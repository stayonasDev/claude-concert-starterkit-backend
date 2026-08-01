package starters.springboot.claude.starterkit.reservation.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import starters.springboot.claude.starterkit.concert.domain.Concert;
import starters.springboot.claude.starterkit.concert.domain.Seat;
import starters.springboot.claude.starterkit.concert.domain.SeatGrade;
import starters.springboot.claude.starterkit.concert.domain.SeatStatus;
import starters.springboot.claude.starterkit.concert.repository.ConcertRepository;
import starters.springboot.claude.starterkit.concert.repository.SeatGradeRepository;
import starters.springboot.claude.starterkit.concert.repository.SeatRepository;
import starters.springboot.claude.starterkit.reservation.domain.Reservation;
import starters.springboot.claude.starterkit.reservation.domain.ReservationSeat;
import starters.springboot.claude.starterkit.reservation.domain.ReservationStatus;
import starters.springboot.claude.starterkit.reservation.repository.ReservationRepository;
import starters.springboot.claude.starterkit.support.ContainerTestSupport;

/**
 * docs/use-cases.md UC-09 대응.
 */
@Transactional
class ReservationExpirationSchedulerTest extends ContainerTestSupport {

    @Autowired
    private ReservationExpirationScheduler scheduler;

    @Autowired
    private ConcertRepository concertRepository;

    @Autowired
    private SeatGradeRepository seatGradeRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    private Long concertId;
    private Long seatGradeId;

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
        this.concertId = concert.getId();

        this.seatGradeId = seatGradeRepository.save(SeatGrade.builder()
                .concertId(concertId)
                .gradeName("VIP")
                .price(BigDecimal.valueOf(200000))
                .totalCount(10)
                .build()).getId();
    }

    private Seat createHeldSeat(String seatNumber, LocalDateTime now) {
        Seat seat = seatRepository.save(Seat.builder()
                .concertId(concertId)
                .seatGradeId(seatGradeId)
                .seatNumber(seatNumber)
                .build());
        seat.hold(1L, now, now.plusMinutes(5));
        return seatRepository.save(seat);
    }

    @Test
    void 만료_시각이_지난_HOLDING_예약은_EXPIRED로_전환되고_좌석이_반환된다() {
        LocalDateTime now = LocalDateTime.now();
        Seat seat = createHeldSeat("A-1", now.minusMinutes(10));

        Reservation reservation = Reservation.builder()
                .userId(1L)
                .concertId(concertId)
                .heldAt(now.minusMinutes(10))
                .holdExpiresAt(now.minusMinutes(5)) // 이미 만료됨
                .build();
        reservation.addReservationSeat(ReservationSeat.builder()
                .seatId(seat.getId())
                .priceSnapshot(BigDecimal.valueOf(200000))
                .build());
        Reservation saved = reservationRepository.save(reservation);

        scheduler.expireHoldingReservations();

        Reservation result = reservationRepository.findById(saved.getId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(ReservationStatus.EXPIRED);

        Seat resultSeat = seatRepository.findById(seat.getId()).orElseThrow();
        assertThat(resultSeat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(resultSeat.getHeldByUserId()).isNull();
    }

    @Test
    void 아직_만료되지_않은_HOLDING_예약은_그대로_유지된다() {
        LocalDateTime now = LocalDateTime.now();
        Seat seat = createHeldSeat("A-2", now);

        Reservation reservation = Reservation.builder()
                .userId(1L)
                .concertId(concertId)
                .heldAt(now)
                .holdExpiresAt(now.plusMinutes(5)) // 아직 유효함
                .build();
        reservation.addReservationSeat(ReservationSeat.builder()
                .seatId(seat.getId())
                .priceSnapshot(BigDecimal.valueOf(200000))
                .build());
        Reservation saved = reservationRepository.save(reservation);

        scheduler.expireHoldingReservations();

        Reservation result = reservationRepository.findById(saved.getId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(ReservationStatus.HOLDING);

        Seat resultSeat = seatRepository.findById(seat.getId()).orElseThrow();
        assertThat(resultSeat.getStatus()).isEqualTo(SeatStatus.HELD);
    }
}
