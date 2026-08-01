# 에러 코드 정의서

> 관련 문서: [API 명세서](./api-spec.md)
> 모든 에러는 `common/exception/GlobalExceptionHandler`(`@RestControllerAdvice`)에서 `common/exception/ErrorCode`에 정의된 코드로 일괄 변환되어, [API 명세서](./api-spec.md#8-공통-에러-응답-포맷)의 공통 포맷으로 응답한다.

## 1. 에러 코드 체계

`{DOMAIN}_{REASON}` 형식의 대문자 스네이크 케이스를 사용한다. HTTP 상태 코드는 에러의 성격에 따라 아래 원칙으로 매핑한다.

| HTTP Status | 사용 기준 |
|---|---|
| 400 Bad Request | 요청 형식 자체가 잘못됨 (필수 필드 누락, 타입 오류) |
| 401 Unauthorized | 인증되지 않음 |
| 403 Forbidden | 인증은 되었으나 권한/조건 미충족 (대기열 미통과, 관리자 아님 등) |
| 404 Not Found | 리소스 없음 |
| 409 Conflict | 상태 충돌 (이미 선점된 좌석, 중복 이메일 등) |
| 422 Unprocessable Entity | 비즈니스 규칙 위반 (취소 기한 초과, 좌석 수 제한 초과 등) |
| 500 Internal Server Error | 예기치 못한 서버 오류 |

## 2. 도메인별 에러 코드

### User / Auth

| 코드 | HTTP | 설명 |
|---|---|---|
| `EMAIL_ALREADY_EXISTS` | 409 | 이미 가입된 이메일로 회원가입 시도 |
| `INVALID_CREDENTIALS` | 401 | 이메일 또는 비밀번호 불일치 |
| `UNAUTHORIZED` | 401 | 인증 토큰 누락/만료 |
| `ACCESS_DENIED` | 403 | 관리자 권한 필요한 API에 일반 사용자가 접근 |

### Concert

| 코드 | HTTP | 설명 |
|---|---|---|
| `CONCERT_NOT_FOUND` | 404 | 존재하지 않는 콘서트 ID |
| `BOOKING_NOT_OPEN` | 403 | 예매 오픈 시각 이전에 선점/예약 요청 (FR-06) |
| `BOOKING_CLOSED` | 403 | 예매 마감 시각 이후 요청 |

### Queue

| 코드 | HTTP | 설명 |
|---|---|---|
| `QUEUE_TOKEN_NOT_FOUND` | 404 | 유효하지 않은 대기 토큰으로 상태 조회 |
| `QUEUE_REQUIRED` | 403 | 대기열 활성화 콘서트에서 입장권 없이(또는 미승급 상태로) 예약 API 호출 (FR-09) |
| `QUEUE_TOKEN_EXPIRED` | 403 | 입장권 TTL 만료 후 예약 API 호출 (승급 스케줄러가 만료 항목을 정리하기 전 짧은 구간에서만 판별 가능 — 정리된 이후에는 `QUEUE_REQUIRED`와 동일하게 취급됨) |

### Reservation / Seat

| 코드 | HTTP | 설명 |
|---|---|---|
| `SEAT_NOT_FOUND` | 404 | 존재하지 않는 좌석 ID로 선점 시도 |
| `SEAT_ALREADY_HELD` | 409 | 이미 HELD/RESERVED 상태인 좌석 선점 시도 (FR-11 핵심 케이스). Redis 락 획득 실패(타임아웃)도 동일 코드로 매핑 |
| `SEAT_LIMIT_EXCEEDED` | 422 | 한 예약에 4석 초과 요청 |
| `ACTIVE_RESERVATION_EXISTS` | 409 | 동일 콘서트에 이미 활성 예약(HOLDING/PENDING_PAYMENT) 보유 |
| `RESERVATION_NOT_FOUND` | 404 | 존재하지 않는 예약 ID |
| `NOT_RESERVATION_OWNER` | 403 | 본인 소유가 아닌 예약에 접근 |
| `RESERVATION_EXPIRED` | 409 | HOLD 만료된 예약으로 결제/조작 시도 |
| `CANCELLATION_DEADLINE_PASSED` | 422 | 공연 D-1 이내 취소 시도 (FR-18) |

### Payment

| 코드 | HTTP | 설명 |
|---|---|---|
| `PAYMENT_FAILED` | 200 (data 없이 error만 포함) | Mock PG가 실패를 반환한 경우. 좌석은 즉시 반환됨 (FR-16) |
| `RESERVATION_ALREADY_PAID` | 409 | 이미 결제 완료된 예약에 재결제 시도 |

### 공통

| 코드 | HTTP | 설명 |
|---|---|---|
| `VALIDATION_FAILED` | 400 | 요청 바디 검증 실패 (Bean Validation) |
| `INTERNAL_SERVER_ERROR` | 500 | 예기치 못한 서버 오류 |
