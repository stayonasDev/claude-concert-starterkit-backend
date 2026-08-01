package starters.springboot.claude.starterkit.concert.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * concert 애그리거트와는 별도의 애그리거트로 두고 concertId를 값으로만 참조한다.
 * (docs/erd.md 설계 노트 - 애그리거트 간에는 ID 참조, 좌석 대량 컬렉션 로딩을 피하기 위함)
 */
@Entity
@Table(
        name = "seat_grades",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_seat_grades_concert_grade",
                columnNames = {"concert_id", "grade_name"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeatGrade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "concert_id", nullable = false)
    private Long concertId;

    @Column(name = "grade_name", nullable = false, length = 20)
    private String gradeName;

    @Column(nullable = false, precision = 10, scale = 0)
    private BigDecimal price;

    @Column(name = "total_count", nullable = false)
    private Integer totalCount;

    @Builder
    private SeatGrade(Long concertId, String gradeName, BigDecimal price, Integer totalCount) {
        this.concertId = concertId;
        this.gradeName = gradeName;
        this.price = price;
        this.totalCount = totalCount;
    }
}
