package starters.springboot.claude.starterkit.queue.infra;

import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

/**
 * 대기열 Redis Sorted Set 조작을 캡슐화한다 (docs/architecture.md 4장).
 *
 * 참고: promote()는 "ZPOPMIN 후 admitted에 ZADD"를 하나의 Lua 스크립트로 묶어 완전히
 * 원자적으로 처리하는 것이 이상적이지만(docs 설계), 이 starter-kit에서는 두 번의 Redis
 * 호출로 단순화했다. 두 호출 사이에 프로세스가 죽으면 해당 배치의 토큰이 두 Set 모두에서
 * 유실될 수 있으나(재진입으로 복구 가능한 손실), Lua 스크립트 작성의 복잡도 대비 학습
 * 목적상 허용 가능한 트레이드오프로 판단했다.
 */
@Repository
@RequiredArgsConstructor
public class RedisWaitingQueueRepository {

    private final StringRedisTemplate redisTemplate;

    public long enqueue(Long concertId, String token) {
        Long seq = redisTemplate.opsForValue().increment(seqKey(concertId));
        long score = seq != null ? seq : System.currentTimeMillis();
        redisTemplate.opsForZSet().add(waitingKey(concertId), token, score);
        return score;
    }

    /** 대기 순번(1부터 시작). 대기열에 없으면 null. */
    public Long rankOf(Long concertId, String token) {
        Long rank = redisTemplate.opsForZSet().rank(waitingKey(concertId), token);
        return rank != null ? rank + 1 : null;
    }

    public boolean isAdmitted(Long concertId, String token, long nowMillis) {
        Double score = redisTemplate.opsForZSet().score(admittedKey(concertId), token);
        return score != null && score > nowMillis;
    }

    public Long admittedExpiresAtMillis(Long concertId, String token) {
        Double score = redisTemplate.opsForZSet().score(admittedKey(concertId), token);
        return score != null ? score.longValue() : null;
    }

    /** waiting 상위 count명을 꺼내 admitted로 승급시키고, 승급된 토큰 목록을 반환한다. */
    public Set<String> promote(Long concertId, int count, Duration admissionTtl) {
        Set<ZSetOperations.TypedTuple<String>> popped =
                redisTemplate.opsForZSet().popMin(waitingKey(concertId), count);
        if (popped == null || popped.isEmpty()) {
            return Set.of();
        }

        double admittedUntilScore = System.currentTimeMillis() + admissionTtl.toMillis();
        Set<ZSetOperations.TypedTuple<String>> admittedTuples = popped.stream()
                .map(tuple -> ZSetOperations.TypedTuple.of(tuple.getValue(), admittedUntilScore))
                .collect(Collectors.toSet());
        redisTemplate.opsForZSet().add(admittedKey(concertId), admittedTuples);

        return popped.stream().map(ZSetOperations.TypedTuple::getValue).collect(Collectors.toSet());
    }

    public void evictExpired(Long concertId, long nowMillis) {
        redisTemplate.opsForZSet().removeRangeByScore(admittedKey(concertId), Double.NEGATIVE_INFINITY, nowMillis);
    }

    private String seqKey(Long concertId) {
        return "queue:%d:seq".formatted(concertId);
    }

    private String waitingKey(Long concertId) {
        return "queue:%d:waiting".formatted(concertId);
    }

    private String admittedKey(Long concertId) {
        return "queue:%d:admitted".formatted(concertId);
    }
}
