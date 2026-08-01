package starters.springboot.claude.starterkit.reservation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Reservation 애그리거트에 속한 라인아이템. seatId는 concert 애그리거트(Seat)에 대한
 * ID 참조일 뿐, JPA 연관관계로 로딩하지 않는다 (docs/erd.md 설계 노트 참고).
 */
@Entity
@Table(
        name = "reservation_seats",
        indexes = {
                @Index(name = "idx_reservation_seats_seat_id", columnList = "seat_id"),
                @Index(name = "idx_reservation_seats_reservation_id", columnList = "reservation_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Column(name = "seat_id", nullable = false)
    private Long seatId;

    @Column(name = "price_snapshot", nullable = false, precision = 10, scale = 0)
    private BigDecimal priceSnapshot;

    @Builder
    private ReservationSeat(Long seatId, BigDecimal priceSnapshot) {
        this.seatId = seatId;
        this.priceSnapshot = priceSnapshot;
    }

    void assignTo(Reservation reservation) {
        this.reservation = reservation;
    }
}
