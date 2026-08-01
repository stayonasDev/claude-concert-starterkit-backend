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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import starters.springboot.claude.starterkit.common.domain.BaseTimeEntity;

@Entity
@Table(
        name = "concerts",
        indexes = {
                @Index(name = "idx_concerts_status_performance_at", columnList = "status, performance_at"),
                @Index(name = "idx_concerts_booking_open_at", columnList = "booking_open_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Concert extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    // docs/database-schema.sql 기준 TEXT (Hibernate @Lob 기본값인 LONGTEXT보다 용량을 줄임)
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 200)
    private String venue;

    @Column(name = "performance_at", nullable = false)
    private LocalDateTime performanceAt;

    @Column(name = "booking_open_at", nullable = false)
    private LocalDateTime bookingOpenAt;

    @Column(name = "booking_close_at", nullable = false)
    private LocalDateTime bookingCloseAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConcertStatus status;

    @Column(name = "poster_image_url", length = 500)
    private String posterImageUrl;

    @Builder
    private Concert(String title, String description, String venue, LocalDateTime performanceAt,
                     LocalDateTime bookingOpenAt, LocalDateTime bookingCloseAt, String posterImageUrl) {
        this.title = title;
        this.description = description;
        this.venue = venue;
        this.performanceAt = performanceAt;
        this.bookingOpenAt = bookingOpenAt;
        this.bookingCloseAt = bookingCloseAt;
        this.posterImageUrl = posterImageUrl;
        this.status = ConcertStatus.UPCOMING;
    }

    // FR-06: 예매 오픈 시각 이전/마감 이후에는 좌석 선점·예약 API가 거부되어야 한다 (docs/requirements.md)
    // 오픈 전/마감 후를 구분해 각각 다른 에러코드(BOOKING_NOT_OPEN/BOOKING_CLOSED)로 응답한다.
    public boolean isBeforeBookingOpen(LocalDateTime now) {
        return now.isBefore(bookingOpenAt);
    }

    public boolean isAfterBookingClose(LocalDateTime now) {
        return now.isAfter(bookingCloseAt);
    }

    public void open() {
        this.status = ConcertStatus.ON_SALE;
    }

    // FR-20: 전 좌석이 RESERVED가 되면 자동 전환 (docs/requirements.md)
    public void markSoldOut() {
        this.status = ConcertStatus.SOLD_OUT;
    }

    public void close() {
        this.status = ConcertStatus.CLOSED;
    }
}
