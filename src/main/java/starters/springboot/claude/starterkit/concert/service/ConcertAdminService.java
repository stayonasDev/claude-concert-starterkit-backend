package starters.springboot.claude.starterkit.concert.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import starters.springboot.claude.starterkit.common.exception.BusinessException;
import starters.springboot.claude.starterkit.common.exception.ErrorCode;
import starters.springboot.claude.starterkit.concert.domain.Concert;
import starters.springboot.claude.starterkit.concert.domain.Seat;
import starters.springboot.claude.starterkit.concert.domain.SeatGrade;
import starters.springboot.claude.starterkit.concert.dto.ConcertCreateRequest;
import starters.springboot.claude.starterkit.concert.dto.ConcertDetailResponse;
import starters.springboot.claude.starterkit.concert.dto.SeatBulkCreateRequest;
import starters.springboot.claude.starterkit.concert.dto.SeatGradeCreateRequest;
import starters.springboot.claude.starterkit.concert.repository.ConcertRepository;
import starters.springboot.claude.starterkit.concert.repository.SeatGradeRepository;
import starters.springboot.claude.starterkit.concert.repository.SeatRepository;

/**
 * 콘서트/좌석 등급/좌석 관리 (docs/use-cases.md UC-12, docs/requirements.md FR-19).
 * 접근 제어(ROLE_ADMIN)는 SecurityConfig에서 경로 기준으로 강제한다.
 */
@Service
@RequiredArgsConstructor
public class ConcertAdminService {

    private final ConcertRepository concertRepository;
    private final SeatGradeRepository seatGradeRepository;
    private final SeatRepository seatRepository;

    @Transactional
    public ConcertDetailResponse createConcert(ConcertCreateRequest request) {
        Concert concert = Concert.builder()
                .title(request.title())
                .description(request.description())
                .venue(request.venue())
                .performanceAt(request.performanceAt())
                .bookingOpenAt(request.bookingOpenAt())
                .bookingCloseAt(request.bookingCloseAt())
                .posterImageUrl(request.posterImageUrl())
                .build();

        Concert saved = concertRepository.save(concert);
        return ConcertDetailResponse.from(saved, List.of());
    }

    @Transactional
    public List<ConcertDetailResponse.SeatGradeResponse> createSeatGrades(
            Long concertId, List<SeatGradeCreateRequest> requests) {
        Concert concert = findConcertOrThrow(concertId);

        List<SeatGrade> seatGrades = requests.stream()
                .map(request -> SeatGrade.builder()
                        .concertId(concert.getId())
                        .gradeName(request.gradeName())
                        .price(request.price())
                        .totalCount(request.totalCount())
                        .build())
                .toList();

        return seatGradeRepository.saveAll(seatGrades).stream()
                .map(ConcertDetailResponse.SeatGradeResponse::from)
                .toList();
    }

    @Transactional
    public int createSeatsBulk(Long concertId, SeatBulkCreateRequest request) {
        findConcertOrThrow(concertId);

        List<Seat> seats = request.seatNumbers().stream()
                .map(seatNumber -> Seat.builder()
                        .concertId(concertId)
                        .seatGradeId(request.seatGradeId())
                        .seatNumber(seatNumber)
                        .build())
                .toList();

        return seatRepository.saveAll(seats).size();
    }

    private Concert findConcertOrThrow(Long concertId) {
        return concertRepository.findById(concertId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONCERT_NOT_FOUND));
    }
}
