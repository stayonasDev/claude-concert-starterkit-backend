package starters.springboot.claude.starterkit.concert.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import starters.springboot.claude.starterkit.common.exception.BusinessException;
import starters.springboot.claude.starterkit.common.exception.ErrorCode;

/**
 * 동시성 제어의 핵심 엔티티 (docs/architecture.md 3장, docs/erd.md 참고).
 * status 전이 자체는 이 엔티티가 책임지지만, "누가 이 전이를 배타적으로 수행하게 만드는가"는
 * Redis 분산락 / DB 비관적 락 두 SeatHoldStrategy 구현체(reservation 도메인)의 책임이다.
 * version은 낙관적 락(@Version)으로, Redis 락 경로에서 CAS 안전망 역할을 겸한다.
 */
@Entity
@Table(
        name = "seats",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_seats_concert_seat_number",
                columnNames = {"concert_id", "seat_number"}
        ),
        indexes = @Index(name = "idx_seats_concert_status", columnList = "concert_id, status")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "concert_id", nullable = false)
    private Long concertId;

    @Column(name = "seat_grade_id", nullable = false)
    private Long seatGradeId;

    @Column(name = "seat_number", nullable = false, length = 20)
    private String seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatStatus status;

    @Column(name = "held_by_user_id")
    private Long heldByUserId;

    @Column(name = "held_at")
    private LocalDateTime heldAt;

    @Column(name = "hold_expires_at")
    private LocalDateTime holdExpiresAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @Builder
    private Seat(Long concertId, Long seatGradeId, String seatNumber) {
        this.concertId = concertId;
        this.seatGradeId = seatGradeId;
        this.seatNumber = seatNumber;
        this.status = SeatStatus.AVAILABLE;
    }

    // FR-11: 동일 좌석에 대한 동시 선점 요청 중 정확히 하나만 성공해야 한다.
    // 이 메서드 자체는 배타성을 보장하지 않으며, 호출 전 락 획득(Redis RLock 또는
    // SELECT ... FOR UPDATE)이 선행되었다는 전제하에 순수 상태 전이만 수행한다.
    public void hold(Long userId, LocalDateTime now, LocalDateTime holdExpiresAt) {
        if (this.status != SeatStatus.AVAILABLE) {
            throw new BusinessException(ErrorCode.SEAT_ALREADY_HELD);
        }
        this.status = SeatStatus.HELD;
        this.heldByUserId = userId;
        this.heldAt = now;
        this.holdExpiresAt = holdExpiresAt;
    }

    // 결제 완료 시 확정 (FR-15)
    public void confirm() {
        if (this.status != SeatStatus.HELD) {
            throw new IllegalStateException("선점 상태가 아닌 좌석은 확정할 수 없습니다. seatId=" + id);
        }
        this.status = SeatStatus.RESERVED;
    }

    // 결제 실패/타임아웃/선점 만료 시 반환 (FR-13, FR-16)
    public void release() {
        this.status = SeatStatus.AVAILABLE;
        this.heldByUserId = null;
        this.heldAt = null;
        this.holdExpiresAt = null;
    }

    public boolean isExpired(LocalDateTime now) {
        return this.status == SeatStatus.HELD
                && this.holdExpiresAt != null
                && this.holdExpiresAt.isBefore(now);
    }
}
