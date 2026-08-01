package starters.springboot.claude.starterkit.concert.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import starters.springboot.claude.starterkit.concert.domain.Concert;
import starters.springboot.claude.starterkit.concert.domain.ConcertStatus;

public interface ConcertRepository extends JpaRepository<Concert, Long> {

    Page<Concert> findByStatus(ConcertStatus status, Pageable pageable);
}
