package starters.springboot.claude.starterkit.concert.dto;

import java.math.BigDecimal;
import starters.springboot.claude.starterkit.concert.domain.Seat;
import starters.springboot.claude.starterkit.concert.domain.SeatGrade;
import starters.springboot.claude.starterkit.concert.domain.SeatStatus;

public record SeatResponse(
        Long seatId,
        String seatNumber,
        String grade,
        BigDecimal price,
        SeatStatus status
) {

    public static SeatResponse from(Seat seat, SeatGrade seatGrade) {
        return new SeatResponse(
                seat.getId(),
                seat.getSeatNumber(),
                seatGrade.getGradeName(),
                seatGrade.getPrice(),
                seat.getStatus()
        );
    }
}
