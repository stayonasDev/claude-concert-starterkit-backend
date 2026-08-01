package starters.springboot.claude.starterkit.concert.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import starters.springboot.claude.starterkit.concert.domain.SeatGrade;

public interface SeatGradeRepository extends JpaRepository<SeatGrade, Long> {

    List<SeatGrade> findByConcertId(Long concertId);
}
