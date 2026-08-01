package starters.springboot.claude.starterkit.concert.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import starters.springboot.claude.starterkit.common.exception.BusinessException;
import starters.springboot.claude.starterkit.common.exception.ErrorCode;
import starters.springboot.claude.starterkit.concert.domain.Concert;
import starters.springboot.claude.starterkit.concert.domain.ConcertStatus;
import starters.springboot.claude.starterkit.concert.domain.Seat;
import starters.springboot.claude.starterkit.concert.domain.SeatGrade;
import starters.springboot.claude.starterkit.concert.dto.ConcertDetailResponse;
import starters.springboot.claude.starterkit.concert.dto.ConcertSummaryResponse;
import starters.springboot.claude.starterkit.concert.dto.SeatResponse;
import starters.springboot.claude.starterkit.concert.repository.ConcertRepository;
import starters.springboot.claude.starterkit.concert.repository.SeatGradeRepository;
import starters.springboot.claude.starterkit.concert.repository.SeatRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConcertQueryService {

    private final ConcertRepository concertRepository;
    private final SeatGradeRepository seatGradeRepository;
    private final SeatRepository seatRepository;

    public Page<ConcertSummaryResponse> findConcerts(ConcertStatus status, Pageable pageable) {
        Page<Concert> concerts = status != null
                ? concertRepository.findByStatus(status, pageable)
                : concertRepository.findAll(pageable);
        return concerts.map(ConcertSummaryResponse::from);
    }

    public ConcertDetailResponse getConcertDetail(Long concertId) {
        Concert concert = findConcertOrThrow(concertId);
        List<SeatGrade> seatGrades = seatGradeRepository.findByConcertId(concertId);
        return ConcertDetailResponse.from(concert, seatGrades);
    }

    public List<SeatResponse> getSeatMap(Long concertId) {
        if (!concertRepository.existsById(concertId)) {
            throw new BusinessException(ErrorCode.CONCERT_NOT_FOUND);
        }

        Map<Long, SeatGrade> seatGradeById = seatGradeRepository.findByConcertId(concertId).stream()
                .collect(Collectors.toMap(SeatGrade::getId, Function.identity()));

        return seatRepository.findByConcertId(concertId).stream()
                .map(seat -> SeatResponse.from(seat, seatGradeById.get(seat.getSeatGradeId())))
                .toList();
    }

    private Concert findConcertOrThrow(Long concertId) {
        return concertRepository.findById(concertId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONCERT_NOT_FOUND));
    }
}
