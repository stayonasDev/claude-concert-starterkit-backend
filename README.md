# 🎫 Concert Ticketing Starter-Kit — Backend

[![CI](https://github.com/stayonasDev/claude-concert-starterkit-backend/actions/workflows/ci.yml/badge.svg)](https://github.com/stayonasDev/claude-concert-starterkit-backend/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.7-brightgreen)

NOL 유니버스·인터파크 티켓 같은 대규모 콘서트 티켓팅 서비스에서 실제로 부딪히는 문제 — **오픈런 트래픽 폭주**, **좌석 동시 선점 충돌**, **예약-결제 정합성** — 를 재현하고 학습할 수 있는 Spring Boot 기반 백엔드 starter-kit입니다.

> ⚠️ 실서비스가 아니라 **학습/재사용 목적의 starter-kit**입니다. Mock 결제, 배포(CD) 파이프라인 미포함 등 의도적으로 축소된 부분이 있습니다. 근거와 범위는 [docs/requirements.md](docs/requirements.md) 참고.

## ✨ 이 프로젝트가 보여주는 것

### 1. 좌석 동시 선점 — 두 가지 동시성 제어 전략을 나란히 비교

같은 요청/응답 스펙을 가진 두 엔드포인트로 Redis 분산락과 DB 비관적 락을 직접 비교할 수 있습니다.

| | `POST /reservations/redis-lock` | `POST /reservations/pessimistic-lock` |
|---|---|---|
| 구현 | Redisson `RLock` | JPA `@Lock(PESSIMISTIC_WRITE)` |
| 강점 | 인메모리라 빠름, 대규모 동시 접속에 유리 | 별도 인프라 없이 트랜잭션 경계 안에서 원자적 처리 |
| 트레이드오프 | 락/DB 반영 분리로 보정 로직 필요, Redis가 새 SPOF | 락 보유 중 DB 커넥션 점유 → 동시 요청 많을수록 처리량 급락 |

자세한 비교는 [docs/tech-decisions.md](docs/tech-decisions.md) 참고.

### 2. 오픈런 대기열

Redis Sorted Set 기반 대기열(`ZADD`/`ZRANK`/`ZPOPMIN`)로 순번 조회와 입장 승급을 구현했습니다. `app.queue.enabled` 설정으로 켜고 끌 수 있어, 대기열 없이 핵심 예약 플로우만 단독으로도 학습할 수 있습니다.

### 3. 예약 → 결제 → 티켓 발급 → 매진 자동 전환

Mock 결제(성공/실패 임의 제어) 성공 시 예약 확정·좌석 확정·티켓 발급이 하나의 트랜잭션으로 처리되고, 콘서트의 전 좌석이 판매되면 `SOLD_OUT`으로 자동 전환됩니다.

## 🖥️ 프론트엔드 데모

이 백엔드를 시연하는 프론트엔드(Next.js)는 별도 저장소에 있습니다: **[claude-concert-starterkit-frontend](https://github.com/stayonasDev/claude-concert-starterkit-frontend)**

## 🛠️ 기술 스택

Spring Boot 4.0.7 · Java 17 · Spring Data JPA (MySQL) · Spring Security (JWT) · Spring Data Redis · Redisson · SpringDoc OpenAPI · JUnit 5 + TestContainers

## 🚀 빠른 시작

```bash
git clone https://github.com/stayonasDev/claude-concert-starterkit-backend.git
cd claude-concert-starterkit-backend
cp .env.example .env         # 최초 1회: 환경변수 파일 생성
docker compose up -d         # MySQL(3307) + Redis(6379) + backend(8080) 전체 스택 기동
```

기동 후 확인:
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

컨테이너 없이 호스트에서 직접 실행하려면 `docker compose up -d mysql redis`로 DB만 띄운 뒤 `./gradlew bootRun`(Windows는 `gradlew.bat bootRun`).

### .env 작성 가이드

`.env.example`을 복사해 아래 변수 값을 채운다 (형식: `변수명=값`).

```
MYSQL_DATABASE=starterkit
MYSQL_USER=starterkit
MYSQL_PASSWORD=starterkit
MYSQL_ROOT_PASSWORD=root
MYSQL_HOST_PORT=3307
REDIS_HOST_PORT=6379
APP_HOST_PORT=8080
APP_JWT_SECRET=<32바이트 이상 임의 문자열>
APP_JWT_VALIDITY_MS=3600000
```

`.env`는 git-ignore 대상이며, 값을 채우지 않아도 `application.yaml`의 기본값으로 동작한다(`./gradlew build`/`test` 시).

### 테스트

```bash
./gradlew test                          # 전체 테스트 (Docker 필요 — TestContainers가 MySQL/Redis를 직접 기동)
./gradlew test --tests "ClassName"      # 특정 클래스만
```
단위(Mockito) · 통합(TestContainers) · 동시성(`ExecutorService`) 3계층으로 구성되어 있습니다. 자세한 전략은 [docs/test-scenarios.md](docs/test-scenarios.md) 참고.

## 📁 프로젝트 구조

도메인 우선(DDD 스타일) 패키지 구조입니다. 계층(`controller/service/...`)이 최상위가 아니라 `user`, `concert`, `queue`, `reservation`, `payment`, `ticket` 같은 도메인 패키지 하위에 있습니다. 상세 구조와 설계 근거는 [docs/architecture.md](docs/architecture.md) 참고.

## 📚 문서

설계 전 과정을 문서로 남겨두었습니다 — 요구사항부터 구현까지의 흐름은 **[docs/README.md](docs/README.md)** 를 시작점으로 보는 것을 권장합니다.

| 문서 | 설명 |
|---|---|
| [requirements.md](docs/requirements.md) | 기능/비기능 요구사항, 범위, 제약사항 |
| [architecture.md](docs/architecture.md) | 패키지 구조, 동시성 제어 설계, 대기열 아키텍처 |
| [tech-decisions.md](docs/tech-decisions.md) | 기술 선택 근거, 두 동시성 전략 비교표 |
| [erd.md](docs/erd.md) / [database-schema.sql](docs/database-schema.sql) | ERD, 참조용 DDL |
| [api-spec.md](docs/api-spec.md) / [error-codes.md](docs/error-codes.md) | API 계약(개발 중엔 Swagger가 최신 소스), 에러 코드 체계 |
| [test-scenarios.md](docs/test-scenarios.md) | 테스트 계층 전략과 시나리오 |
| [ci-cd.md](docs/ci-cd.md) | CI 파이프라인 설명 |

Claude Code 등 AI 에이전트가 이 저장소에서 작업할 때 참고하는 가이드는 [CLAUDE.md](CLAUDE.md)에 있습니다.

## 🔧 트러블슈팅

개발 중 실제로 마주친 문제와 원인 분석, 해결 과정을 기록합니다. 제목을 클릭하면 상세 내용이 펼쳐집니다.

<details>
<summary><strong>좌석 동시 선점 시 SEAT_ALREADY_HELD 대신 500 에러가 발생한 문제</strong></summary>

#### 문제 상황

프론트엔드 데모에서 동일 좌석에 서로 다른 사용자 8명이 `redis-lock`/`pessimistic-lock` 두 엔드포인트에 동시 요청을 보내는 시나리오를 재현했다. 기대한 결과는 1명만 성공하고 나머지 7명은 `409 SEAT_ALREADY_HELD`를 받는 것이었지만, 실제로는 1명만 성공하고 나머지 대부분이 `500 INTERNAL_SERVER_ERROR`를 받았다. 두 락 전략 모두에서 동일하게 재현됐다.

#### 원인 분석

`ReservationFacade.holdAndBuildLine()`이 좌석 락(`SeatHoldStrategy.hold()`)을 획득하기 **전에** 가격 스냅샷을 만들기 위해 `seatRepository.findById()`로 `Seat`를 먼저 조회하고 있었다.

```java
// 수정 전
private ReservationSeat holdAndBuildLine(SeatHoldStrategy strategy, Long seatId, Long userId,
                                          LocalDateTime holdExpiresAt) {
    Seat seat = seatRepository.findById(seatId)          // ← 락 획득 전, 잠금 없는 조회
            .orElseThrow(() -> new BusinessException(ErrorCode.SEAT_NOT_FOUND));
    SeatGrade seatGrade = seatGradeRepository.findById(seat.getSeatGradeId())
            .orElseThrow(() -> new BusinessException(ErrorCode.SEAT_NOT_FOUND));

    strategy.hold(seatId, userId, holdExpiresAt);          // ← 락은 여기서 획득
    ...
}
```

`spring.jpa.open-in-view=true`(OSIV) 설정 때문에 HTTP 요청 하나당 영속성 컨텍스트(1차 캐시) 하나가 요청 전체에 걸쳐 유지된다. 이 잠금 없는 선행 조회가 1차 캐시에 오래된 `Seat` 엔티티(`status=AVAILABLE`, 오래된 `version`)를 심어버렸고, 이후 `SeatHoldStrategy`가 락을 획득한 뒤 **같은 seatId로 다시** `findById`/`findByIdForUpdate`를 호출해도 Hibernate는 DB를 재조회하지 않고 캐시된 그 인스턴스를 그대로 반환했다.

그 결과 패자 요청들도 `Seat.hold()`의 상태 검사(`status != AVAILABLE`)를 오래된 값 때문에 통과해버렸다. 그리고 커밋 시점에 실제 DB의 `version`과 메모리상 엔티티의 `version`이 어긋나 `ObjectOptimisticLockingFailureException`이 발생했다. 이 예외는 `BusinessException`이 아니어서 `GlobalExceptionHandler`의 범용 `Exception` 핸들러로 흘러 들어갔고, 결국 `SEAT_ALREADY_HELD` 대신 `INTERNAL_SERVER_ERROR`로 응답했다.

#### 해결

가격 스냅샷에 필요한 `Seat`/`SeatGrade` 조회를 `strategy.hold()` 호출 **이후**로 옮겼다. 이렇게 하면 각 전략이 락을 획득한 뒤 수행하는 조회가 해당 요청에서의 첫 조회가 되므로, 1차 캐시가 오염되지 않고 항상 락 획득 시점 기준으로 최신 상태를 읽는다.

```java
// 수정 후
private ReservationSeat holdAndBuildLine(SeatHoldStrategy strategy, Long seatId, Long userId,
                                          LocalDateTime holdExpiresAt) {
    strategy.hold(seatId, userId, holdExpiresAt);          // ← 락을 먼저 획득

    Seat seat = seatRepository.findById(seatId)            // ← 락 획득 후 첫 조회이므로 최신 상태
            .orElseThrow(() -> new BusinessException(ErrorCode.SEAT_NOT_FOUND));
    SeatGrade seatGrade = seatGradeRepository.findById(seat.getSeatGradeId())
            .orElseThrow(() -> new BusinessException(ErrorCode.SEAT_NOT_FOUND));
    ...
}
```

#### 결과

동일 좌석에 서로 다른 사용자 8명이 동시에 요청하는 시나리오를 다시 실행해 검증했다. 수정 전에는 1명 성공 + 다수의 500 에러였던 결과가, 수정 후에는 1명 성공 + 나머지 7명 전원 정상적인 `409 SEAT_ALREADY_HELD`로 바뀌었다. `redis-lock`/`pessimistic-lock` 두 전략 모두에서 동일하게 확인했다.

</details>

<details>
<summary><strong>예외가 발생해도 로그에 아무 것도 남지 않던 문제</strong></summary>

#### 문제 상황

위 500 에러의 원인을 찾기 위해 `docker logs`로 백엔드 로그를 확인했지만, 에러가 다수 발생한 시간대에 로그가 완전히 비어 있었다.

#### 원인 분석

`GlobalExceptionHandler.handleUnexpectedException(Exception e)`이 예외 파라미터 `e`를 전혀 사용하지 않고 곧바로 `INTERNAL_SERVER_ERROR` 응답만 만들고 있었다.

```java
// 수정 전
@ExceptionHandler(Exception.class)
public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception e) {
    return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
            .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR));
}
```

`BusinessException`이 아닌 모든 예외가 이 핸들러로 흘러 들어가 스택트레이스 하나 남기지 않고 조용히 삼켜지고 있었다.

#### 해결

SLF4J `Logger`를 추가하고 예외를 잡는 즉시 스택트레이스를 남기도록 고쳤다.

```java
// 수정 후
@ExceptionHandler(Exception.class)
public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception e) {
    log.error("Unexpected exception", e);
    return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
            .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR));
}
```

#### 결과

이 로그 덕분에 위 `ObjectOptimisticLockingFailureException`의 정확한 발생 지점과 실패한 SQL 구문을 곧바로 확인할 수 있었다. 앞으로도 예기치 못한 예외가 발생하면 원인을 즉시 추적할 수 있다.

</details>

<details>
<summary><strong>HOLD 만료 시각이 타임존 없이 내려가 클라이언트가 9시간을 오해한 문제</strong></summary>

#### 문제 상황

프론트엔드에서 좌석 선점 성공 후 `holdExpiresAt`(HOLD 만료 5분 카운트다운)을 표시했는데, 방금 선점에 성공했음에도 남은 시간이 항상 `0:00`으로 표시됐다.

#### 원인 분석

`ReservationResult.holdExpiresAt` 등 응답의 시각 필드는 `LocalDateTime`으로 직렬화된다. `LocalDateTime`은 타임존 정보를 갖지 않으므로 JSON에도 `2026-08-01T08:41:23.456`처럼 오프셋/`Z` 표기 없이 내려간다. 서버는 UTC 기준으로 값을 만들지만, 이 문자열을 받는 클라이언트(브라우저)가 UTC가 아닌 타임존(KST, UTC+9)에 있으면 표기가 없는 ISO 문자열을 **로컬 시각**으로 해석해버린다(ECMAScript 명세 동작). 그 결과 실제로는 5분 뒤인 시각이 클라이언트 계산상 9시간 전(과거)으로 취급됐다.

단순 날짜 표시(포맷팅해서 다시 보여주는 경우)는 파싱과 표시가 같은 타임존을 쓰기 때문에 우연히 원래 숫자와 같게 보여 문제가 드러나지 않았다. 반면 "현재 시각과의 차이"를 계산하는 카운트다운 같은 로직에서는 오차가 그대로 드러났다.

#### 해결

백엔드 응답 스펙(`LocalDateTime` 기반)은 유지하고, 이를 소비하는 프론트엔드에서 타임존 표기가 없는 문자열을 UTC로 명시해 파싱하도록 헬퍼를 추가해 대응했다(표기가 없으면 `Z`를 붙여 `Date`를 생성). 백엔드 응답 자체는 API 계약을 바꾸지 않기 위해 그대로 두었다.

#### 결과

HOLD 카운트다운이 실제 만료 시각까지 남은 시간을 정확히 보여준다. 이 API를 새로 소비하는 클라이언트가 같은 함정에 빠지지 않도록, 시각 필드는 별도 표기가 없는 한 **UTC 기준**이라는 점을 여기 남겨둔다(추후 응답 DTO를 `Instant`/오프셋 포함 타입으로 바꾸는 것도 고려할 만하다).

</details>

<details>
<summary><strong>결제 실패 시 좌석 반환/예약 취소가 함께 롤백되던 문제</strong></summary>

#### 문제 상황

프론트엔드에서 결제 실패(`forceFail`) 시뮬레이션을 테스트하는 중, 결제 실패 응답(`PAYMENT_FAILED`)은 정상적으로 받았지만 이후 콘서트 상세 페이지에서 방금 실패한 좌석이 여전히 선택 불가 상태로 남아 있는 것을 발견했다.

#### 원인 분석

`PaymentService.pay()`가 하나의 `@Transactional`로 감싸여 있는데, 결제 실패 시 좌석 반환/예약 취소/실패 기록을 처리한 직후 `BusinessException(PAYMENT_FAILED)`을 던져 컨트롤러까지 전파시켰다.

```java
// 수정 전
if (!chargeResult.success()) {
    handlePaymentFailure(reservation, payment);   // 좌석 반환, 예약 취소, 실패 기록
    throw new BusinessException(ErrorCode.PAYMENT_FAILED);
}
```

Spring의 기본 트랜잭션 정책상 `RuntimeException`이 전파되면 트랜잭션 전체가 롤백된다. `handlePaymentFailure`가 `pay()`와 같은 트랜잭션 안에서 실행됐으므로, 방금 반환한 좌석과 취소한 예약까지 이 롤백에 함께 휩쓸려 사라졌다 — 응답은 결제 실패로 보이지만 DB는 여전히 좌석 `HELD`/예약 `HOLDING` 상태로 남아 재선점이 불가능했다(FR-16 위반).

#### 해결

`PaymentFailureService`를 새로 만들어 결제 실패 시 좌석 반환/예약 취소/실패 기록을 `REQUIRES_NEW`로 별도 트랜잭션에 즉시 커밋하도록 분리했다. 처음엔 `reservation`/`payment` 엔티티를 그대로 넘겨 새 트랜잭션에서 `merge`하는 방식으로 구현했으나, 트랜잭션 경계 너머로 전달된 엔티티를 merge하면 버전 충돌(`StaleObjectStateException`)이 발생해, id/원시값만 넘기고 그 트랜잭션 안에서 새로 조회 → 수정 → 저장까지 전부 끝내도록 다시 설계했다.

```java
// 수정 후 — PaymentService.pay()
if (!chargeResult.success()) {
    paymentFailureService.recordFailureAndReleaseSeats(reservation.getId(), amount, command.method());
    throw new BusinessException(ErrorCode.PAYMENT_FAILED);
}
```

```java
// PaymentFailureService — 별도 트랜잭션에서 즉시 커밋
@Component
@RequiredArgsConstructor
public class PaymentFailureService {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailureAndReleaseSeats(Long reservationId, BigDecimal amount, PaymentMethod method) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        // payment 실패 기록 저장, reservation.cancel() + 저장, 좌석마다 release() + 저장
    }
}
```

#### 결과

Docker로 백엔드를 재기동해 프론트에서 좌석 선택 → 결제 실패 시뮬레이션 → 좌석이 즉시 다시 선택 가능해지는지 확인했고, 결제 성공 경로도 함께 재검증했다. 기존 `PaymentServiceTest`/`PaymentControllerTest`는 클래스 전체가 하나의(아직 커밋 안 된) 트랜잭션으로 실행되는 구조라 `REQUIRES_NEW`가 그 데이터를 격리 원칙상 볼 수 없는 문제가 있어, `TestTransaction`으로 예약 생성 시점의 커밋과 검증 시점의 재조회를 맞춰 함께 통과시켰다(실제 운영에서는 좌석 선점과 결제가 서로 다른 HTTP 요청이라 문제 없다).

</details>

<details>
<summary><strong>좌석 등급 생성 응답에 id가 없어 관리자 화면을 구현할 수 없던 문제</strong></summary>

#### 문제 상황

관리자 화면(콘서트 등록 → 좌석 등급 등록 → 좌석 일괄 생성 3단계 마법사)을 구현하는 중, 방금 만든 좌석 등급으로 좌석을 생성하려면 `seatGradeId`가 필요한데 좌석 등급 생성 API의 응답만으로는 그 id를 알아낼 방법이 없었다.

#### 원인 분석

좌석 일괄 생성(`POST /admin/concerts/{id}/seats/bulk`)은 `SeatBulkCreateRequest.seatGradeId`를 요구하지만, 좌석 등급 생성(`POST /admin/concerts/{id}/seat-grades`) 응답과 콘서트 상세 조회 응답(`ConcertDetailResponse.seatGrades`)이 공유하는 `SeatGradeResponse`에는 `gradeName`/`price`/`totalCount`만 있고 `id`가 없었다.

```java
// 수정 전
public record SeatGradeResponse(String gradeName, BigDecimal price, Integer totalCount) {
    public static SeatGradeResponse from(SeatGrade seatGrade) {
        return new SeatGradeResponse(seatGrade.getGradeName(), seatGrade.getPrice(), seatGrade.getTotalCount());
    }
}
```

#### 해결

`SeatGrade` 엔티티에는 이미 `id`가 있었으므로, `SeatGradeResponse`에 `id` 필드를 추가하고 `from()`에서 채우도록 고쳤다. 이 레코드를 포지셔널 생성자로 직접 만드는 다른 코드가 없어(전부 `SeatGradeResponse.from(...)`을 거침) 필드 추가만으로 하위 호환되게 고칠 수 있었다.

```java
// 수정 후
public record SeatGradeResponse(Long id, String gradeName, BigDecimal price, Integer totalCount) {
    public static SeatGradeResponse from(SeatGrade seatGrade) {
        return new SeatGradeResponse(
                seatGrade.getId(), seatGrade.getGradeName(), seatGrade.getPrice(), seatGrade.getTotalCount());
    }
}
```

#### 결과

프론트엔드가 좌석 등급 생성 응답의 `id`를 그대로 좌석 일괄 생성 요청에 사용할 수 있게 됐다. 관리자 계정으로 콘서트 생성 → 좌석 등급 생성 → 그 등급으로 좌석 생성까지 3단계를 전부 실행해, 콘서트 상세 페이지에 실제로 반영되는 것까지 확인했다.

</details>
