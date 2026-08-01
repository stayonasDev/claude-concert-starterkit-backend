package starters.springboot.claude.starterkit.concert.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import starters.springboot.claude.starterkit.concert.domain.Seat;
import starters.springboot.claude.starterkit.concert.domain.SeatStatus;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByConcertId(Long concertId);

    List<Seat> findByConcertIdAndStatus(Long concertId, SeatStatus status);

    /**
     * DB 비관적 락 전략(PessimisticLockSeatHoldStrategy) 전용 조회.
     * SELECT ... FOR UPDATE로 행 배타 락을 걸며, 호출한 트랜잭션이 커밋/롤백될 때까지 유지된다.
     * (docs/architecture.md 3.3절 대응)
     *
     * 여러 좌석을 동시에 선점할 때는 데드락 방지를 위해 반드시 seatId 오름차순으로
     * 정렬한 뒤 이 메서드를 순차 호출해야 한다 — 호출 순서 보장은 서비스 계층의 책임이다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.id = :id")
    Optional<Seat> findByIdForUpdate(@Param("id") Long id);

    // FR-20: 잔여 판매 가능 좌석(AVAILABLE/HELD)이 있는지 확인해 콘서트 매진 여부를 판단한다.
    boolean existsByConcertIdAndStatusIn(Long concertId, List<SeatStatus> statuses);
}
