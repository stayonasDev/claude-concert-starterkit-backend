# 테스트 시나리오

> 관련 문서: [요구사항 명세서](./requirements.md) · [유즈케이스](./use-cases.md) · [API 명세서](./api-spec.md)
> 테스트 전략: 단위 테스트(Mockito) + 통합 테스트(`@SpringBootTest` + **TestContainers**로 실제 MySQL/Redis 컨테이너 기동) + 동시성 테스트(`ExecutorService` 기반 다중 스레드 시나리오)

## 1. 테스트 계층 전략

| 계층 | 도구 | 대상 |
|---|---|---|
| 단위 테스트 | JUnit 5 + Mockito(`@ExtendWith(MockitoExtension.class)`) | Repository를 mock으로 대체해 순수 비즈니스 로직만 검증 (예: `UserServiceUnitTest`, `ReservationCancelServiceUnitTest`). 컨테이너 기동이 없어 초 단위로 실행된다 |
| 통합 테스트 | `@SpringBootTest` + TestContainers(MySQL+Redis) | 컨트롤러→서비스→DB/Redis 전체 플로우, Repository 쿼리·락(`@Lock`) 동작을 포함해 검증. 대다수의 테스트가 이 계층에 속한다 |
| 동시성 테스트 | `@SpringBootTest` + TestContainers + `ExecutorService` | 좌석 선점 두 전략, 대기열 순번 부여의 정합성 검증 (핵심) |

**계획 대비 실제 구현 노트**: 최초 설계 단계에서는 Repository 슬라이스 테스트(`@DataJpaTest`)와 컨트롤러 슬라이스 테스트(`@WebMvcTest`)를 별도 계층으로 두려 했으나, 실제로는 두 계층 모두 위 "통합 테스트"에 흡수되어 구현되었다. 이유: (1) `PESSIMISTIC_WRITE` 락 동작은 슬라이스 테스트보다 `PessimisticLockSeatHoldStrategyTest`의 동시성 테스트로 검증하는 편이 실제 경합 상황을 더 직접적으로 재현한다. (2) 컨트롤러는 JWT 인증 필터 체인(`SecurityConfig`)·대기열 인터셉터(`QueueAdmissionInterceptor`)와 강하게 결합되어 있어, `@WebMvcTest`로 이들을 슬라이스에 맞게 재구성하는 것보다 `@SpringBootTest(webEnvironment=MOCK)` + `@AutoConfigureMockMvc`로 전체 컨텍스트를 띄우는 편이 실제 동작과 가장 가깝고 유지보수 비용도 낮았다.

TestContainers는 `@DynamicPropertySource`로 컨테이너의 실제 포트를 `spring.datasource.url`/`spring.data.redis.host`,`port`에 주입하며, 로컬 Docker Compose 설정과 동일한 이미지(MySQL 8, Redis 7)를 사용해 환경 차이를 최소화한다.

## 2. 인증 (TS-AUTH)

| ID | 시나리오 | 유형 | 관련 FR/UC |
|---|---|---|---|
| TS-AUTH-01 | 정상 이메일/비밀번호로 회원가입 시 201과 함께 계정이 생성된다 | 통합 | FR-01, UC-01 |
| TS-AUTH-01-1 | 이미 존재하는 이메일로 가입 시 `409 EMAIL_ALREADY_EXISTS`를 반환한다 | 통합 | FR-01 |
| TS-AUTH-02 | 올바른 자격증명으로 로그인 시 토큰이 발급된다 | 통합 | FR-02, UC-02 |
| TS-AUTH-02-1 | 잘못된 비밀번호로 로그인 시 `401 INVALID_CREDENTIALS` | 통합 | FR-02 |

## 3. 콘서트 조회 (TS-CONCERT)

| ID | 시나리오 | 유형 | 관련 FR/UC |
|---|---|---|---|
| TS-CONCERT-01 | 상태별 콘서트 목록 필터링이 정상 동작한다 | 통합 | FR-03, UC-03 |
| TS-CONCERT-02 | 좌석맵 조회 시 각 좌석의 실시간 상태(AVAILABLE/HELD/RESERVED)가 정확히 반영된다 | 통합 | FR-05, UC-04 |

## 4. 대기열 (TS-QUEUE)

| ID | 시나리오 | 유형 | 관련 FR/UC |
|---|---|---|---|
| TS-QUEUE-01 | 대기열 진입 시 순번(rank)이 순차적으로 증가한다 (동시 진입 100명 시뮬레이션) | 통합 | FR-07, UC-05 |
| TS-QUEUE-02 | 상태 폴링 API가 `ZRANK` 기반으로 정확한 순번을 반환한다 | 통합 | FR-07 |
| TS-QUEUE-03 | 입장권 없이 예약 API 호출 시 `403 QUEUE_REQUIRED`를 반환한다 | 통합 | FR-09 |
| TS-QUEUE-04 | 승급 스케줄러 실행 후 상위 K명이 `waiting`에서 `admitted`로 정확히 이동한다 | 통합 | UC-13 |
| TS-QUEUE-05 | 입장권 TTL 만료 후 예약 API 호출 시 `403 QUEUE_TOKEN_EXPIRED` | 통합 | FR-09 |

## 5. 예약/좌석 선점 (TS-RESV) — 핵심 동시성 검증

| ID | 시나리오 | 유형 | 관련 FR/UC |
|---|---|---|---|
| TS-RESV-01 | 좌석이 `AVAILABLE`일 때 Redis 락 방식으로 정상 선점된다 | 통합 | FR-10, UC-06 |
| TS-RESV-02 | 좌석이 `AVAILABLE`일 때 DB 비관적 락 방식으로 정상 선점된다 | 통합 | FR-10, UC-06 |
| **TS-RESV-03** | **[Redis 락] 동일 좌석에 대해 100개 스레드가 동시에 선점 요청 → 정확히 1건만 성공, 나머지 99건은 `409 SEAT_ALREADY_HELD`** | **동시성** | **FR-11, FR-12, NFR-01** |
| **TS-RESV-04** | **[DB 비관적 락] 동일 좌석에 대해 100개 스레드가 동시에 선점 요청 → 정확히 1건만 성공** | **동시성** | **FR-11, FR-12, NFR-01** |
| TS-RESV-05 | 서로 다른 좌석 여러 개를 동시에 선점하는 다중 스레드 시나리오에서 각 좌석마다 1건씩만 성공한다 (데드락 없이 완료) | 동시성 | FR-11 |
| TS-RESV-06 | HOLD 후 만료 시간 경과 시 스케줄러가 예약을 `EXPIRED`로 전환하고 좌석을 `AVAILABLE`로 반환한다 | 통합 | FR-13, UC-09 |
| TS-RESV-07 | 본인 예약/티켓 목록만 조회되고 타인 예약은 조회되지 않는다 | 통합 | FR-17, UC-10 |
| TS-RESV-08 | 공연 D-1 이내 취소 시도 시 `422 CANCELLATION_DEADLINE_PASSED` | 통합 | FR-18, UC-11 |
| TS-RESV-09 | 대기열 미활성화 콘서트에서는 입장권 없이도 예약 API가 정상 동작한다 (플래그 off 확인, NFR 근거) | 통합 | 가정사항 4 |
| TS-RESV-10 | [부하 비교] 동일 시나리오(100/500/1000 동시 요청)를 Redis 락과 DB 비관적 락 두 전략에 각각 적용해 응답시간/처리량을 비교 측정한다 | 성능(수동/k6) | NFR-01, [tech-decisions.md](./tech-decisions.md) 비교표 검증 |
| TS-RESV-11 | 예매 마감 시각(`bookingCloseAt`) 이후 선점 요청 시 `403 BOOKING_CLOSED`를 반환한다 (오픈 전 `BOOKING_NOT_OPEN`과 구분) | 통합 | FR-06 |

### TS-RESV-03/04 상세 설계 (동시성 테스트 표준 패턴)

```java
int threadCount = 100;
ExecutorService executor = Executors.newFixedThreadPool(32);
CountDownLatch latch = new CountDownLatch(threadCount);
AtomicInteger successCount = new AtomicInteger();

for (int i = 0; i < threadCount; i++) {
    executor.submit(() -> {
        try {
            seatHoldStrategy.hold(seatId, randomUserId(), null);
            successCount.incrementAndGet();
        } catch (BusinessException e) {
            // SEAT_ALREADY_HELD 예상됨
        } finally {
            latch.countDown();
        }
    });
}
latch.await();

assertThat(successCount.get()).isEqualTo(1);
```
이 패턴을 `RedisLockSeatHoldStrategy`, `PessimisticLockSeatHoldStrategy` 각각에 동일하게 적용해 두 구현체의 정합성을 동등하게 검증한다.

## 6. 결제 (TS-PAY)

| ID | 시나리오 | 유형 | 관련 FR/UC |
|---|---|---|---|
| TS-PAY-01 | Mock 결제 성공 시 Payment(PAID)/Reservation(CONFIRMED)/Seat(RESERVED)/Ticket(ISSUED)가 하나의 트랜잭션으로 일관되게 반영된다 | 통합 | FR-15, UC-07 |
| TS-PAY-02 | Mock 결제 실패 시 좌석이 즉시 `AVAILABLE`로 반환되고 다른 사용자가 재선점 가능하다 | 통합 | FR-16, UC-08 |
| TS-PAY-03 | 이미 결제된 예약에 재결제 시도 시 `409 RESERVATION_ALREADY_PAID` | 통합 | - |
| TS-PAY-04 | HOLD 만료된 예약으로 결제 시도 시 `409 RESERVATION_EXPIRED` | 통합 | FR-13 |

## 7. 관리자 (TS-ADMIN)

| ID | 시나리오 | 유형 | 관련 FR/UC |
|---|---|---|---|
| TS-ADMIN-01 | 관리자가 콘서트/좌석 등급/좌석을 정상 등록한다 | 통합 | FR-19, UC-12 |
| TS-ADMIN-02 | 일반 사용자가 관리자 API 호출 시 `403 ACCESS_DENIED` | 통합 | FR-19 |

## 8. 회귀/자동화 정책

- 모든 TS-RESV-03, TS-RESV-04(핵심 동시성 테스트)는 CI 파이프라인에서 **반드시** 실행되며, flaky 방지를 위해 `latch.await(timeout)`과 스레드풀 크기를 고정값으로 관리한다 ([ci-cd.md](./ci-cd.md) 참고)
- TS-RESV-10(성능 비교)은 CI에 포함하지 않고 별도 수동/주기 실행 대상으로 분류한다 (부하테스트는 CI 러너 리소스에 부담)
