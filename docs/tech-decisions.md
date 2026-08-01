# 기술 선택 근거 문서

> 관련 문서: [아키텍처 문서](./architecture.md) · [요구사항 명세서](./requirements.md)

## 1. 기확정 스택의 역할

| 기술 | 이 도메인에서의 역할 | 근거 |
|---|---|---|
| Spring Boot 4 / Web MVC | REST API 서버 | DI/AOP 기반으로 `SeatHoldStrategy` 같은 전략 교체가 자연스럽고, 학습용 starter-kit으로서 보일러플레이트가 적음 |
| Spring Data JPA + MySQL | 좌석/예약/결제/티켓의 정합성 있는 트랜잭션 처리 | 티켓팅 도메인은 "이중 판매 방지"라는 강한 정합성 요구가 있어 RDB의 ACID·행 단위 락이 필수. `@Lock(PESSIMISTIC_WRITE)`가 JPA 표준 스펙으로 지원됨 |
| Spring Data Redis | 대기열(Sorted Set), 좌석 캐시, 분산락 기반 인프라 | 저지연 인메모리 자료구조가 필요한 대기열/락 요구에 적합 |
| Spring Security | 로그인/인증, 관리자 API(콘서트 등록 등) 보호 | 사용자별 예약 소유권 검증(본인 예약만 조회/취소) 등 인가 로직에 필요 |
| SpringDoc OpenAPI | 두 가지 락 전략 API를 나란히 문서화 | `redis-lock`/`pessimistic-lock` 두 엔드포인트에 각각 트레이드오프 설명을 Swagger 어노테이션으로 남겨 학습 자료로 활용 가능 |

## 2. Redis 분산락(Redisson) vs DB 비관적 락 비교

| 항목 | Redis 분산락 (Redisson) | DB 비관적 락 (`SELECT ... FOR UPDATE`) |
|---|---|---|
| 처리량/지연 | 락 자체는 인메모리라 매우 빠름. DB 트랜잭션을 짧게 유지 가능해 커넥션 점유 최소화 → 대량 동시 요청에 유리 | 락 보유 동안 DB 커넥션+트랜잭션을 계속 점유 → 커넥션 풀이 병목이 되어 동시 요청이 많을수록 처리량 급락 |
| 구현 복잡도 | Redisson 도입/학습 필요, 락-데이터 분리로 인한 보정 로직(CAS 등) 추가 고려 필요 | JPA `@Lock` 애노테이션 하나로 구현 가능, 별도 인프라 학습 불필요 |
| 장애 시나리오 | Redis 장애/네트워크 파티션 시 예약 전체가 막힘(새 SPOF 추가). 락 보유 중 애플리케이션이 죽으면 leaseTime 만료 전까지 좌석이 묶임(false lock) | DB 장애 시 이미 서비스 불가 상태이므로 추가 SPOF 아님. 데드락 감지·락 대기 타임아웃이 DB 엔진(InnoDB)이 자동 처리 |
| 확장성(수평 확장) | 애플리케이션 서버를 여러 대로 늘려도 Redis가 가벼워 초당 수만 락 처리 가능 → 오픈런 같은 폭주 트래픽에 적합 | 결국 MySQL 커넥션 풀/행 락 경합에 수렴 → 동시 접속자가 늘수록 확장성 한계가 뚜렷 |
| 정합성 보장 | 락과 실제 DB 반영이 분리되어 있어 부분 실패(락은 잡았는데 DB 갱신 실패) 대비 보정 로직 필요 | 트랜잭션 경계 안에서 원자적으로 처리되어 별도 보정 불필요 |
| 운영/디버깅 | 락 상태를 Redis CLI로 직접 들여다봐야 하고, "왜 안 풀렸는지" 추적이 DB 락보다 어려움 | `SHOW ENGINE INNODB STATUS`, 슬로우 쿼리 로그 등 성숙한 DB 운영 도구 활용 가능 |
| starter-kit 결론 | 대규모 동시 접속(오픈런) 시나리오를 재현/학습하기에 적합 | 소규모/단순 서비스에서 인프라 추가 없이 정합성을 확보하는 기본기 학습에 적합 |

**Redisson 채택 근거**: `spring-data-redis`의 `RedisTemplate`만으로 분산락을 직접 구현하면 `SETNX` + Lua 스크립트 기반 원자적 해제, 재진입성, lease 자동 연장(watchdog) 등을 직접 작성해야 하며 버그 위험이 큽니다. Redisson은 이미 검증된 `RLock` 구현체(Pub/Sub 기반 대기, Lua 기반 원자적 락 해제, watchdog)를 제공하므로 학습용 starter-kit에서 "직접 락을 짜는 실수"를 피하게 해준다.

→ **실제 적용 의존성**: `org.redisson:redisson` (코어 라이브러리만). `org.redisson:redisson-spring-boot-starter`는 이 프로젝트의 Spring Boot 4.0.7 기준 아직 호환되지 않아(구버전 패키지 `org.springframework.boot.autoconfigure.data.redis.RedisProperties`를 직접 참조 → Boot 4에서 `org.springframework.boot.data.redis.autoconfigure.DataRedisProperties`로 재배치되며 `ClassNotFoundException` 발생) 사용하지 않는다. `RedissonClient` 빈은 자동설정에 의존하지 않고 `common/config/RedissonConfig`에서 직접 생성한다.

**환경 참고 — TestContainers와 최신 Docker Engine(29+) 호환성**: Testcontainers 1.21.3(현재 최신)이 내부적으로 Docker 연결 확인 시 오래된 API 버전(1.24~1.32)을 사용해 핸드셰이크하는데, Docker Engine 29부터 최소 지원 API 버전이 1.40으로 올라가면서 `BadRequestException(400)` / `Could not find a valid Docker environment` 오류가 발생할 수 있다. `src/test/resources/docker-java.properties`에 `api.version=1.44`를 고정해 우회하며, Testcontainers가 API 버전 자동 협상을 지원하는 상위 버전으로 올라가면 이 파일은 제거해도 된다.

**환경 참고 — Spring Boot 4의 Jackson 3 전환**: Spring Boot 4는 기본 JSON 라이브러리를 Jackson 2(`com.fasterxml.jackson.databind.ObjectMapper`)에서 **Jackson 3**(`tools.jackson.databind.ObjectMapper`, 그룹ID가 `tools.jackson`으로 변경됨)로 전환했다. 컨트롤러 테스트에서 요청 바디를 직렬화할 `ObjectMapper`를 주입받을 때 구버전 패키지로 임포트하면 `NoSuchBeanDefinitionException`이 발생하므로 반드시 `tools.jackson.databind.ObjectMapper`를 사용해야 한다. 같은 이유로 `@AutoConfigureMockMvc`도 패키지가 `org.springframework.boot.webmvc.test.autoconfigure`로 재배치되었다(구 `org.springframework.boot.test.autoconfigure.web.servlet`가 아님) — Boot 4의 모듈 세분화(각 기술별 `spring-boot-*-autoconfigure`/`spring-boot-*-test` 아티팩트 분리)에 따른 변화다.

## 3. 대기열에 Redis Sorted Set을 쓰는 이유

- `ZADD`/`ZRANK`/`ZRANGE`: O(log N)으로 순번 삽입·조회 가능 → "내 앞에 몇 명 있는지" 실시간 응답에 필수
- `ZPOPMIN`: 원자적으로 선착순 상위 K명을 꺼낼 수 있어 승급 스케줄러 로직이 단순해짐
- `ZREMRANGEBYSCORE`: score를 만료시각으로 설계하면 만료된 입장권을 한 번의 명령으로 일괄 정리 가능
- List(`LPUSH`/`RPOP`)로도 FIFO는 가능하지만 "임의 원소의 순번 조회" 기능이 없어 대기열 상태 폴링 API를 만들 수 없음 → Sorted Set이 사실상 유일한 적합한 선택지

## 4. TestContainers / Docker Compose 연동을 염두에 둔 설계 포인트

- `application.yaml`을 프로필(`local`/`test`/`docker`)로 분리하고, `spring.datasource.*`/`spring.data.redis.*` 값을 환경변수로 주입받도록 설계 → 로컬 개발은 Docker Compose(MySQL+Redis), CI 테스트는 TestContainers로 동일한 설정 키를 그대로 오버라이드 가능
- `RedissonConfig`의 host/port도 `spring.data.redis.host/port`를 그대로 참조하도록 만들어, Redis 연결 설정이 한 곳(`application.yaml`)에서만 관리되도록 함 (Redisson과 Spring Data Redis가 서로 다른 설정을 참조하면 TestContainers 전환 시 누락되기 쉬움)
- `SeatHoldStrategy` 인터페이스를 둔 덕분에, 통합 테스트에서 `ExecutorService`로 동시 요청을 재현하는 동시성 테스트를 두 구현체 각각에 대해 동일한 테스트 코드로 작성 가능
- Repository/서비스 계층이 MySQL/Redis 구체 드라이버에 직접 의존하지 않고 Spring Data 추상화(`JpaRepository`, `RedisTemplate`/`RedissonClient`)에만 의존하도록 하여, `@DynamicPropertySource`로 TestContainers 컨테이너의 실제 포트를 주입하는 패턴 적용이 쉬워짐
- 대기열 승급 스케줄러(`@Scheduled`)는 테스트 환경에서 `app.queue.admission-rate`를 설정으로 제어 가능하게 만들어, 통합 테스트에서 승급 타이밍을 예측 가능하게 조정할 수 있도록 함

## 5. 향후 검토 대상 (Not Decided)

starter-kit 범위 밖이거나 이후 확장 시 재검토가 필요한 항목:

| 항목 | 현재 결정 | 향후 검토 방향 |
|---|---|---|
| 결제 연동 | Mock PG | 실제 PG(토스페이먼츠, 아임포트 등) 연동 시 별도 어댑터 계층 추가 |
| DB 마이그레이션 | 참조용 DDL 문서(`database-schema.sql`) | 실제 운영 시 Flyway/Liquibase로 버전 관리 전환 권장 |
| 배포 파이프라인 | 미정 (CI는 빌드+테스트까지만) | 배포 대상(온프레미스/클라우드) 확정 후 CD 단계 추가 |
| 낙관적 락 전략 | 미구현, `seats.version` 컬럼만 사전 확보 | 세 번째 동시성 전략으로 확장 가능 |
