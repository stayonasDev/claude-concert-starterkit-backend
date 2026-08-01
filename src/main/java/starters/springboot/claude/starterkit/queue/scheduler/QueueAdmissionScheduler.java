package starters.springboot.claude.starterkit.queue.scheduler;

import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import starters.springboot.claude.starterkit.concert.domain.Concert;
import starters.springboot.claude.starterkit.concert.domain.ConcertStatus;
import starters.springboot.claude.starterkit.concert.repository.ConcertRepository;
import starters.springboot.claude.starterkit.queue.infra.RedisWaitingQueueRepository;

/**
 * 대기 중인 사용자를 순번대로 입장 허용시킨다 (docs/architecture.md 4장, docs/use-cases.md UC-13).
 */
@Component
@RequiredArgsConstructor
public class QueueAdmissionScheduler {

    private static final Duration ADMISSION_TTL = Duration.ofMinutes(10);

    private final RedisWaitingQueueRepository queueRepository;
    private final ConcertRepository concertRepository;

    @Value("${app.queue.admission-rate:10}")
    private int admissionRate;

    @Scheduled(fixedDelayString = "${app.queue.admission-interval-ms:2000}")
    public void promoteWaitingUsers() {
        List<Concert> onSaleConcerts = concertRepository.findByStatus(ConcertStatus.ON_SALE, Pageable.unpaged())
                .getContent();

        long now = System.currentTimeMillis();
        for (Concert concert : onSaleConcerts) {
            queueRepository.promote(concert.getId(), admissionRate, ADMISSION_TTL);
            queueRepository.evictExpired(concert.getId(), now);
        }
    }
}
