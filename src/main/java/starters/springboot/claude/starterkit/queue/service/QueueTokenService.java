package starters.springboot.claude.starterkit.queue.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import starters.springboot.claude.starterkit.common.exception.BusinessException;
import starters.springboot.claude.starterkit.common.exception.ErrorCode;
import starters.springboot.claude.starterkit.queue.dto.QueueEnterResponse;
import starters.springboot.claude.starterkit.queue.dto.QueueStatusResponse;
import starters.springboot.claude.starterkit.queue.infra.RedisWaitingQueueRepository;

/**
 * 대기열 진입/상태 조회 (docs/use-cases.md UC-05).
 */
@Service
@RequiredArgsConstructor
public class QueueTokenService {

    // 승급 스케줄러의 fixedDelay(2초)와 admission-rate를 근거로 한 대략적인 추정치일 뿐,
    // 실측 처리량 기반 추정은 이후 개선 대상이다.
    private static final double ADMISSION_INTERVAL_SECONDS = 2.0;

    private final RedisWaitingQueueRepository queueRepository;

    @Value("${app.queue.admission-rate:10}")
    private int admissionRate;

    public QueueEnterResponse enter(Long concertId) {
        String token = UUID.randomUUID().toString();
        queueRepository.enqueue(concertId, token);
        long rank = rankOrThrow(concertId, token);
        return new QueueEnterResponse(token, rank, estimateWaitSeconds(rank));
    }

    public QueueStatusResponse getStatus(Long concertId, String token) {
        long now = System.currentTimeMillis();

        Long admittedUntilMillis = queueRepository.admittedExpiresAtMillis(concertId, token);
        if (admittedUntilMillis != null && admittedUntilMillis > now) {
            return QueueStatusResponse.admitted(toLocalDateTime(admittedUntilMillis));
        }

        Long rank = queueRepository.rankOf(concertId, token);
        if (rank == null) {
            throw new BusinessException(ErrorCode.QUEUE_TOKEN_NOT_FOUND);
        }
        return QueueStatusResponse.waiting(rank, estimateWaitSeconds(rank));
    }

    public boolean isAdmitted(Long concertId, String token) {
        return queueRepository.isAdmitted(concertId, token, System.currentTimeMillis());
    }

    /**
     * 대기열 미통과(NOT_ADMITTED)와 입장권 만료(EXPIRED)를 구분해 서로 다른 에러코드로 응답하기
     * 위한 판별. 승급 스케줄러(QueueAdmissionScheduler)가 만료된 admitted 항목을 주기적으로
     * evictExpired()로 제거하므로, 제거되기 전 짧은 구간에서만 EXPIRED로 판별 가능하다 — 이후에는
     * NOT_ADMITTED와 동일하게 보인다(허용 가능한 트레이드오프, docs/architecture.md 4장 참고).
     */
    public AdmissionCheckResult checkAdmission(Long concertId, String token) {
        Long expiresAtMillis = queueRepository.admittedExpiresAtMillis(concertId, token);
        if (expiresAtMillis == null) {
            return AdmissionCheckResult.NOT_ADMITTED;
        }
        return expiresAtMillis > System.currentTimeMillis() ? AdmissionCheckResult.ADMITTED : AdmissionCheckResult.EXPIRED;
    }

    public enum AdmissionCheckResult {
        ADMITTED, EXPIRED, NOT_ADMITTED
    }

    private long rankOrThrow(Long concertId, String token) {
        Long rank = queueRepository.rankOf(concertId, token);
        if (rank == null) {
            throw new BusinessException(ErrorCode.QUEUE_TOKEN_NOT_FOUND);
        }
        return rank;
    }

    private long estimateWaitSeconds(long rank) {
        if (admissionRate <= 0) {
            return -1;
        }
        long batches = (long) Math.ceil((double) rank / admissionRate);
        return (long) (batches * ADMISSION_INTERVAL_SECONDS);
    }

    private LocalDateTime toLocalDateTime(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }
}
