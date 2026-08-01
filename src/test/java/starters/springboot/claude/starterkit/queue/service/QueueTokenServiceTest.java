package starters.springboot.claude.starterkit.queue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import starters.springboot.claude.starterkit.common.exception.BusinessException;
import starters.springboot.claude.starterkit.common.exception.ErrorCode;
import starters.springboot.claude.starterkit.queue.dto.QueueEnterResponse;
import starters.springboot.claude.starterkit.queue.dto.QueueStatusResponse;
import starters.springboot.claude.starterkit.queue.infra.RedisWaitingQueueRepository;
import starters.springboot.claude.starterkit.support.ContainerTestSupport;

/**
 * docs/use-cases.md UC-05, UC-13 대응.
 * 콘서트 ID를 테스트마다 다르게 사용해 Redis 키 충돌(테스트 간 데이터 공유)을 피한다.
 */
class QueueTokenServiceTest extends ContainerTestSupport {

    @Autowired
    private QueueTokenService queueTokenService;

    @Autowired
    private RedisWaitingQueueRepository queueRepository;

    @Test
    void 대기열_진입시_1번_순번을_부여받는다() {
        long concertId = 9001L;

        QueueEnterResponse response = queueTokenService.enter(concertId);

        assertThat(response.rank()).isEqualTo(1L);
        assertThat(response.token()).isNotBlank();
    }

    @Test
    void 여러_명이_진입하면_순번이_순차적으로_증가한다() {
        long concertId = 9002L;
        int entrantCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch latch = new CountDownLatch(entrantCount);
        AtomicLong maxRank = new AtomicLong(0);

        for (int i = 0; i < entrantCount; i++) {
            executor.submit(() -> {
                try {
                    QueueEnterResponse response = queueTokenService.enter(concertId);
                    maxRank.updateAndGet(current -> Math.max(current, response.rank()));
                } finally {
                    latch.countDown();
                }
            });
        }

        awaitUninterruptibly(latch);
        executor.shutdown();

        assertThat(maxRank.get()).isEqualTo(entrantCount);
    }

    @Test
    void 존재하지_않는_토큰으로_상태_조회시_예외가_발생한다() {
        long concertId = 9003L;

        BusinessException exception = assertThrows(BusinessException.class,
                () -> queueTokenService.getStatus(concertId, "no-such-token"));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.QUEUE_TOKEN_NOT_FOUND);
    }

    @Test
    void 승급된_토큰은_ADMITTED_상태로_조회된다() {
        long concertId = 9004L;
        QueueEnterResponse entered = queueTokenService.enter(concertId);

        Set<String> promoted = queueRepository.promote(concertId, 10, Duration.ofMinutes(10));
        assertThat(promoted).contains(entered.token());

        QueueStatusResponse status = queueTokenService.getStatus(concertId, entered.token());
        assertThat(status.status()).isEqualTo("ADMITTED");
        assertThat(queueTokenService.isAdmitted(concertId, entered.token())).isTrue();
    }

    @Test
    void 승급_인원_이후_순번은_대기_상태로_유지된다() {
        long concertId = 9005L;
        QueueEnterResponse first = queueTokenService.enter(concertId);
        QueueEnterResponse second = queueTokenService.enter(concertId);

        queueRepository.promote(concertId, 1, Duration.ofMinutes(10)); // 1명만 승급

        assertThat(queueTokenService.isAdmitted(concertId, first.token())).isTrue();
        assertThat(queueTokenService.isAdmitted(concertId, second.token())).isFalse();

        QueueStatusResponse secondStatus = queueTokenService.getStatus(concertId, second.token());
        assertThat(secondStatus.status()).isEqualTo("WAITING");
    }

    private void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
