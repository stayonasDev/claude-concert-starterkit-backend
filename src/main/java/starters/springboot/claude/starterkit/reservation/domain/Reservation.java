package starters.springboot.claude.starterkit.reservation.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import starters.springboot.claude.starterkit.common.domain.BaseTimeEntity;
import starters.springboot.claude.starterkit.common.lock.LockStrategyType;

/**
 * 예약 헤더(애그리거트 루트). ReservationSeat(라인아이템)을 하나의 애그리거트로 묶어
 * 함께 생성/삭제되도록 cascade + orphanRemoval을 적용한다 (docs/erd.md 1:N 관계 참고).
 */
@Entity
@Table(
        name = "reservations",
        indexes = {
                @Index(name = "idx_reservations_user_id", columnList = "user_id"),
                @Index(name = "idx_reservations_status_hold_expires_at", columnList = "status, hold_expires_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "concert_id", nullable = false)
    private Long concertId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    // 두 동시성 전략(Redis/DB 락) 중 어느 것으로 처리되었는지 기록 (비교 학습용, docs/tech-decisions.md)
    @Enumerated(EnumType.STRING)
    @Column(name = "lock_strategy", length = 20)
    private LockStrategyType lockStrategy;

    @Column(name = "held_at")
    private LocalDateTime heldAt;

    @Column(name = "hold_expires_at", nullable = false)
    private LocalDateTime holdExpiresAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReservationSeat> reservationSeats = new ArrayList<>();

    @Builder
    private Reservation(Long userId, Long concertId, LockStrategyType lockStrategy,
                         LocalDateTime heldAt, LocalDateTime holdExpiresAt) {
        this.userId = userId;
        this.concertId = concertId;
        this.lockStrategy = lockStrategy;
        this.heldAt = heldAt;
        this.holdExpiresAt = holdExpiresAt;
        this.status = ReservationStatus.HOLDING;
    }

    public void addReservationSeat(ReservationSeat reservationSeat) {
        reservationSeats.add(reservationSeat);
        reservationSeat.assignTo(this);
    }

    // FR-15: 결제 성공 시 예약 확정
    public void confirm(LocalDateTime confirmedAt) {
        if (this.status != ReservationStatus.HOLDING && this.status != ReservationStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("결제 확정할 수 없는 예약 상태입니다. status=" + status);
        }
        this.status = ReservationStatus.CONFIRMED;
        this.confirmedAt = confirmedAt;
    }

    // FR-13: HOLD 만료 시 스케줄러가 호출
    public void expire() {
        this.status = ReservationStatus.EXPIRED;
    }

    // FR-16, FR-18: 결제 실패 또는 사용자 취소
    public void cancel() {
        this.status = ReservationStatus.CANCELLED;
    }

    public boolean isHoldExpired(LocalDateTime now) {
        return this.status == ReservationStatus.HOLDING && this.holdExpiresAt.isBefore(now);
    }
}
