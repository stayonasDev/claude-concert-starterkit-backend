# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

Spring Boot 4.0.7 / Java 17 기반 콘서트 티켓팅(NOL 유니버스·인터파크 티켓 유사) backend starter-kit. 오픈런 트래픽 대응(대기열)과 좌석 동시 선점 정합성(Redis 분산락 vs DB 비관적 락 비교)을 학습 가능한 형태로 재현하는 것이 핵심 목적이다.

**설계 문서가 `docs/`에 매우 상세히 정리되어 있다 — 코드를 고치기 전에 먼저 확인할 것:**
- [docs/README.md](docs/README.md) — 문서 인덱스, 구현 현황
- [docs/architecture.md](docs/architecture.md) — 패키지 구조, 동시성 제어 설계, 대기열 아키텍처
- [docs/tech-decisions.md](docs/tech-decisions.md) — 기술 선택 근거, **환경 관련 알려진 이슈와 우회법**
- [docs/api-spec.md](docs/api-spec.md), [docs/error-codes.md](docs/error-codes.md) — API/에러 코드 계약
- [docs/erd.md](docs/erd.md), [docs/database-schema.sql](docs/database-schema.sql) — 스키마
- [docs/test-scenarios.md](docs/test-scenarios.md) — 테스트 계층 전략과 시나리오 ID

## 주요 명령어

Windows에서는 `./gradlew` 대신 `gradlew.bat`을 사용한다.

### 빌드 / 실행
```bash
./gradlew build          # 전체 빌드 (테스트 포함)
./gradlew build -x test  # 테스트 스킵하고 빌드
cp .env.example .env     # 최초 1회: 환경변수 파일 생성 (민감정보, git-ignore 대상)
docker compose up -d     # MySQL(3307)+Redis(6379)+backend(8080) 전체 스택 기동
./gradlew bootRun        # (컨테이너 대신 호스트에서 직접 실행할 경우) docker compose로 mysql/redis만 띄운 뒤 실행
```
Swagger UI: `http://localhost:8080/swagger-ui.html`

`docker compose up -d`는 `backend`도 이미지로 빌드해 함께 띄운다(`Dockerfile`, 멀티스테이지: JDK로 `bootJar` 빌드 → JRE로 실행). `backend` 컨테이너는 서비스명 기반 주소(`mysql:3306`, `redis:6379`)를, 호스트에서 직접 `bootRun`할 때는 `application.yaml`의 기본값(`localhost:3307`, `localhost:6379`)을 쓴다 — 이 차이는 `docker-compose.yml`의 `backend.environment`가 `SPRING_DATASOURCE_URL` 등을 덮어쓰는 방식으로 처리한다.

**환경변수(`.env`)**: DB 자격증명, JWT 시크릿 등 민감정보는 `.env`(git-ignore)로 관리하며 `.env.example`이 템플릿이다. `application.yaml`은 `${VAR:default}` 형태로 참조하므로 `.env` 없이 `./gradlew build`/`test`를 실행해도 개발용 기본값으로 정상 동작한다 — `.env`는 `docker compose`가 실행 디렉터리에서 자동으로 읽는다(별도 `--env-file` 불필요).

### 테스트
```bash
./gradlew test                                      # 전체 테스트 (Docker 필요 — TestContainers가 MySQL/Redis를 직접 기동)
./gradlew test --tests ClassName                    # 특정 클래스
./gradlew test --tests ClassName.methodName          # 특정 메서드
./gradlew test --tests "*ServiceTest"                # 패턴 매칭
```

## 아키텍처

### 패키지 구조 — 도메인 우선(DDD 스타일), 단일 Gradle 모듈

계층 우선(`controller/`, `service/` 최상위)이 아니라 **도메인 패키지 하위에 `controller/service/repository/domain/dto`**를 두는 구조다. `user`, `concert`, `queue`, `reservation`, `payment`, `ticket` 도메인 + 횡단 관심사를 담는 `common`(config/exception/response/lock)으로 구성. 새 기능을 추가할 때는 해당 도메인 패키지 안에서 계층을 찾는다.

### 핵심 설계: 좌석 선점 동시성 제어 — 두 전략을 나란히 제공

이 프로젝트의 가장 중요한 설계 포인트다. `reservation/service/SeatHoldStrategy` 인터페이스를 `RedisLockSeatHoldStrategy`(Redisson `RLock`)와 `PessimisticLockSeatHoldStrategy`(JPA `@Lock(PESSIMISTIC_WRITE)`)가 각각 구현하며, `ReservationFacade`가 `LockStrategyType`에 따라 전략을 선택해 오케스트레이션한다. 같은 요청/응답 스펙을 별도 엔드포인트(`POST /reservations/redis-lock`, `POST /reservations/pessimistic-lock`)로 노출한다 — 트레이드오프 비교가 목적이므로 쿼리 파라미터 분기를 쓰지 않는다.

`ReservationFacade.reserve()`는 **의도적으로 통짜 `@Transactional`이 아니다**: Redis 락 전략은 "락 해제 전에 DB 커밋이 끝나야 한다"는 전제로 설계되어 있어, 상위 트랜잭션으로 묶으면 그 전제가 깨진다. 대신 각 저장 단위가 개별 트랜잭션 경계를 갖고, 다중 좌석 요청의 부분 실패 시 이미 선점된 좌석을 명시적으로 반환(release)한다. 여러 좌석을 동시에 선점할 때는 데드락 방지를 위해 반드시 `seatId` 오름차순으로 순차 처리한다.

`seats.status`가 두 전략 모두에서 정합성의 단일 진실 소스다 — Redis는 락/캐시일 뿐 최종 판단 기준이 아니다.

### 대기열 시스템

`queue/infra/RedisWaitingQueueRepository`가 Redis Sorted Set(`ZADD`/`ZRANK`/`ZPOPMIN`/`ZREMRANGEBYSCORE`)을 캡슐화한다. 대기열 진입 API는 **익명(비인증)**이며 요청마다 새 UUID 토큰을 발급한다 — 동일 사용자 재진입 방지는 의도적으로 미구현(구현하려면 인증 강제 + 전체 흐름 변경 필요, 근거는 docs/README.md 참고). `QueueAdmissionInterceptor`(`WebConfig`에 `/reservations/redis-lock`, `/reservations/pessimistic-lock` 경로에만 등록됨)가 좌석 선점 요청 전에 `X-Concert-Id`/`X-Queue-Token` 헤더로 입장권을 검증하며, 미진입(`QUEUE_REQUIRED`)과 만료(`QUEUE_TOKEN_EXPIRED`)를 구분한다. `app.queue.enabled=false`(기본값)에서는 검증 없이 통과한다. `QueueAdmissionScheduler`가 대기 → 입장 승급과 만료 정리를 주기적으로 수행한다.

### 결제 → 좌석 확정 → 티켓 발급 → 매진 전환

`PaymentService.pay()`가 Payment/Reservation/Seat/Ticket 갱신과 콘서트 매진(SOLD_OUT) 자동 전환을 하나의 트랜잭션으로 처리한다(FR-15, FR-20). Mock PG(`MockPgClient`)는 `forceFail` boolean으로 성공/실패를 결정적으로 시뮬레이션한다.

### 인증/인가

JWT 기반 무상태 인증(`user/security/JwtTokenProvider`, `JwtAuthenticationFilter`). `SecurityConfig`가 경로 기준으로 접근 제어를 정의한다: 회원가입/로그인/콘서트 조회(GET)/대기열은 `permitAll`, `/api/v1/admin/**`는 `hasRole("ADMIN")`, 나머지는 인증 필요. 컨트롤러는 `Authentication` 파라미터 + `AuthenticatedUser.from(authentication)`으로 현재 사용자 ID/역할을 얻는다.

### 공통 응답/에러

모든 API가 `common/response/ApiResponse<T>` 봉투(`{success, data, error}`)로 응답한다. 비즈니스 예외는 `BusinessException(ErrorCode)`를 던지면 `GlobalExceptionHandler`(`@RestControllerAdvice`)가 `ErrorCode`에 매핑된 HTTP 상태/메시지로 일괄 변환한다. 새 에러 케이스를 추가할 때는 `common/exception/ErrorCode`에 상수를 추가하고 `docs/error-codes.md`도 함께 갱신한다.

## 환경 관련 알려진 이슈 (반드시 숙지)

- **Jackson 3**: Spring Boot 4는 기본 ObjectMapper가 `tools.jackson.databind.ObjectMapper`다(구버전 `com.fasterxml.jackson.databind.ObjectMapper` 아님, groupId도 다름). 테스트에서 `ObjectMapper`를 주입받을 때 반드시 `tools.jackson.databind.ObjectMapper`를 import할 것.
- **`@AutoConfigureMockMvc` 패키지 변경**: Boot 4에서 `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`로 재배치됨 (구 `org.springframework.boot.test.autoconfigure.web.servlet` 아님).
- **Redisson**: `redisson-spring-boot-starter`는 Boot 4의 리패키징된 자동설정 클래스와 충돌해 `ClassNotFoundException`을 낸다. 순수 코어 `org.redisson:redisson`만 쓰고, `RedissonClient` 빈은 `RedissonConfig`에서 직접 생성한다.
- **TestContainers ↔ Docker Engine 29+**: 오래된 API 버전 핸드셰이크 문제로 `Could not find a valid Docker environment` 오류가 날 수 있다 — `src/test/resources/docker-java.properties`(`api.version=1.44`)로 우회 중이다. 삭제하지 말 것.
- **ddl-auto가 환경별로 다름**: 로컬(`application.yaml`)은 `validate`(스키마 소스는 `docs/database-schema.sql`, docker-compose 초기화 스크립트로 적용), 테스트(`ContainerTestSupport`)는 `update`다. 이유: 하나의 공유 TestContainers 컨테이너 위에서 서로 다른 `@TestPropertySource` 컨텍스트가 각자 `create-drop`을 쓰면 아직 살아있는 다른 컨텍스트의 스케줄러 빈이 DROP된 테이블을 조회하다 깨지는 경합이 있었다.
- 엔티티를 수정하면 `docs/database-schema.sql`도 함께 갱신해야 한다 — 안 그러면 로컬 `bootRun`이 `ddl-auto=validate`에서 스키마 불일치로 즉시 실패한다.

## 테스트 작성 규칙

- 모든 서비스 로직 테스트는 `support/ContainerTestSupport`를 상속해 TestContainers 기반 실제 MySQL/Redis로 검증하는 것이 기본값이다(단위 테스트는 예외 — 아래 참고). MockMvc 컨트롤러 테스트도 슬라이스(`@WebMvcTest`)가 아니라 `@SpringBootTest(webEnvironment=MOCK)` + `@AutoConfigureMockMvc`로 전체 컨텍스트를 띄운다 — JWT 필터 체인/대기열 인터셉터와 결합도가 높기 때문이다.
- `ContainerTestSupport.bearerToken(userId, role)`로 실제 로그인 없이 `Authorization` 헤더 값을 만들 수 있다.
- 좌석 선점처럼 순수 비즈니스 로직 검증이 목적이고 의존성이 적은 서비스는 `@ExtendWith(MockitoExtension.class)` 기반 단위 테스트(예: `UserServiceUnitTest`, `ReservationCancelServiceUnitTest`)로 컨테이너 없이 빠르게 검증한다.
- 동시성 검증(좌석 선점 두 전략, 대기열 순번 부여)은 `ExecutorService`/`CountDownLatch` 패턴을 쓴다(`docs/test-scenarios.md` TS-RESV-03/04 참고). 여러 좌석 동시 선점 테스트에서 데드락 방지 규칙(seatId 오름차순)이 깨지지 않았는지 함께 확인할 것.
- 테스트 클래스 전체에 `@Transactional`을 붙일지는 신중히 판단한다 — 동시성 테스트 클래스는 붙이면 안 되고(트랜잭션이 스레드 간 격리를 깨뜨림), 순차 실행되는 일반 서비스 테스트는 데이터 격리를 위해 붙이는 편이 안전하다.
