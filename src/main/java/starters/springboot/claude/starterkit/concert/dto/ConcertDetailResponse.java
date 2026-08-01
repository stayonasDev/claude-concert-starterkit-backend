package starters.springboot.claude.starterkit.concert.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import starters.springboot.claude.starterkit.concert.domain.Concert;
import starters.springboot.claude.starterkit.concert.domain.ConcertStatus;
import starters.springboot.claude.starterkit.concert.domain.SeatGrade;

public record ConcertDetailResponse(
        Long id,
        String title,
        String description,
        String venue,
        LocalDateTime performanceAt,
        LocalDateTime bookingOpenAt,
        LocalDateTime bookingCloseAt,
        ConcertStatus status,
        String posterImageUrl,
        List<SeatGradeResponse> seatGrades
) {

    public static ConcertDetailResponse from(Concert concert, List<SeatGrade> seatGrades) {
        List<SeatGradeResponse> seatGradeResponses = seatGrades.stream()
                .map(SeatGradeResponse::from)
                .toList();

        return new ConcertDetailResponse(
                concert.getId(),
                concert.getTitle(),
                concert.getDescription(),
                concert.getVenue(),
                concert.getPerformanceAt(),
                concert.getBookingOpenAt(),
                concert.getBookingCloseAt(),
                concert.getStatus(),
                concert.getPosterImageUrl(),
                seatGradeResponses
        );
    }

    public record SeatGradeResponse(Long id, String gradeName, BigDecimal price, Integer totalCount) {

        public static SeatGradeResponse from(SeatGrade seatGrade) {
            return new SeatGradeResponse(
                    seatGrade.getId(), seatGrade.getGradeName(), seatGrade.getPrice(), seatGrade.getTotalCount());
        }
    }
}
