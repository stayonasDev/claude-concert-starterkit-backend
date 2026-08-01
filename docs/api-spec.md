# API 명세서

> 관련 문서: [유즈케이스](./use-cases.md) · [아키텍처 문서](./architecture.md) · [에러코드 정의서](./error-codes.md)
> Base URL: `/api/v1`
> 실제 배포 시 이 문서 대신 SpringDoc OpenAPI(Swagger UI, `/swagger-ui.html`)가 최신 소스이며, 이 문서는 구현 착수 전 합의된 계약(contract)으로 유지한다.
> Swagger UI는 `OpenApiConfig`(`common/config`)에서 도메인별 태그(Auth/Concert/Queue/Reservation/Payment/Ticket/Admin)로 API를 그룹핑하고, 우측 상단 `Authorize` 버튼으로 JWT Bearer 토큰을 입력하면 인증이 필요한 API도 바로 호출해볼 수 있다.
> 공통 응답 포맷은 `ApiResponse<T>` (`common/response`)를 따른다: `{ "success": boolean, "data": T, "error": { "code": string, "message": string } | null }`

## 1. 인증 (Auth / User)

### 1.1 회원가입
```
POST /api/v1/users/signup
```
**Request Body**
```json
{
  "email": "user@example.com",
  "password": "P@ssw0rd!",
  "name": "홍길동",
  "phone": "010-1234-5678"
}
```
**Response 201**
```json
{ "success": true, "data": { "id": 1, "email": "user@example.com", "name": "홍길동" }, "error": null }
```
**Errors**: `409 EMAIL_ALREADY_EXISTS`

### 1.2 로그인
```
POST /api/v1/auth/login
```
**Request Body**: `{ "email": "user@example.com", "password": "P@ssw0rd!" }`
**Response 200**: `{ "success": true, "data": { "accessToken": "..." }, "error": null }`
**Errors**: `401 INVALID_CREDENTIALS`

## 2. 콘서트 (Concert)

> 콘서트의 전체 좌석이 모두 판매(RESERVED)되면, 결제 확정 시점에 `status`가 자동으로 `SOLD_OUT`으로 전환된다 (FR-20, `PaymentService.markSoldOutIfNoSeatsRemain`).

### 2.1 콘서트 목록 조회
```
GET /api/v1/concerts?status=ON_SALE&page=0&size=20
```
**Response 200**
```json
{
  "success": true,
  "data": {
    "content": [
      { "id": 1, "title": "2026 World Tour", "venue": "잠실 올림픽 주경기장", "performanceAt": "2026-09-01T19:00:00", "status": "ON_SALE" }
    ],
    "page": 0, "size": 20, "totalElements": 1
  }
}
```

### 2.2 콘서트 상세 조회
```
GET /api/v1/concerts/{concertId}
```
**Response 200**
```json
{
  "success": true,
  "data": {
    "id": 1, "title": "2026 World Tour", "description": "...", "venue": "잠실 올림픽 주경기장",
    "performanceAt": "2026-09-01T19:00:00", "bookingOpenAt": "2026-08-01T10:00:00",
    "bookingCloseAt": "2026-08-31T23:59:59", "status": "ON_SALE",
    "seatGrades": [ { "gradeName": "VIP", "price": 200000, "totalCount": 100 } ]
  }
}
```
**Errors**: `404 CONCERT_NOT_FOUND`

### 2.3 좌석맵 조회
```
GET /api/v1/concerts/{concertId}/seats
```
**Response 200**
```json
{
  "success": true,
  "data": [
    { "seatId": 101, "seatNumber": "A-1", "grade": "VIP", "price": 200000, "status": "AVAILABLE" },
    { "seatId": 102, "seatNumber": "A-2", "grade": "VIP", "price": 200000, "status": "HELD" }
  ]
}
```

## 3. 대기열 (Queue)

### 3.1 대기열 진입
```
POST /api/v1/queue/{concertId}/enter
```
익명(비로그인)으로도 호출 가능하다. 토큰 발급 시 사용자 신원과 연결하지 않으므로(요청마다 새 UUID 토큰 발급), 동일 사용자의 중복 진입을 막는 별도 검증은 없다 — 이를 구현하려면 이 API에 인증을 강제해야 하고 대기열 전체 흐름·테스트가 크게 바뀌므로 이 starter-kit 범위에서는 의도적으로 생략했다.
**Response 200**
```json
{ "success": true, "data": { "token": "qtoken-abc123", "rank": 1523, "estimatedWaitSeconds": 305 }, "error": null }
```

### 3.2 대기열 상태 조회 (폴링)
```
GET /api/v1/queue/{concertId}/status?token=qtoken-abc123
```
**Response 200 (대기 중)**
```json
{ "success": true, "data": { "status": "WAITING", "rank": 480, "estimatedWaitSeconds": 96 }, "error": null }
```
**Response 200 (입장 허용)**
```json
{ "success": true, "data": { "status": "ADMITTED", "admittedTokenExpiresAt": "2026-08-01T10:15:00" }, "error": null }
```
**Errors**: `404 QUEUE_TOKEN_NOT_FOUND`

## 4. 예약 (Reservation) — 좌석 선점 핵심 API

두 엔드포인트는 동일한 요청/응답 스펙을 가지며 내부 동시성 제어 전략만 다르다 (근거: [architecture.md](./architecture.md#3-좌석-선점-동시성-제어--두-전략-비교-설계)).

### 4.1 좌석 선점 — Redis 분산락 방식
```
POST /api/v1/reservations/redis-lock
Header: Authorization: Bearer {accessToken}
Header (app.queue.enabled=true인 콘서트만 필수): X-Concert-Id: 1, X-Queue-Token: qtoken-abc123
```
**Request Body**
```json
{ "concertId": 1, "seatIds": [101, 102] }
```
**Response 201**
```json
{
  "success": true,
  "data": {
    "reservationId": 5001, "status": "HOLDING", "lockStrategy": "REDIS",
    "holdExpiresAt": "2026-08-01T10:05:00",
    "seats": [ { "seatId": 101, "priceSnapshot": 200000 } ]
  }
}
```
**Errors**: `409 SEAT_ALREADY_HELD`, `403 QUEUE_REQUIRED`(입장권 없음/미승급), `403 QUEUE_TOKEN_EXPIRED`(입장권 만료), `403 BOOKING_NOT_OPEN`(오픈 전), `403 BOOKING_CLOSED`(마감 후), `422 SEAT_LIMIT_EXCEEDED`(최대 4매 초과), `409 ACTIVE_RESERVATION_EXISTS`

### 4.2 좌석 선점 — DB 비관적 락 방식
```
POST /api/v1/reservations/pessimistic-lock
```
요청/응답/에러 스펙은 4.1과 동일하며 `lockStrategy: "PESSIMISTIC"`으로 응답.

### 4.3 예약 목록 조회 (본인)
```
GET /api/v1/reservations
Header: Authorization: Bearer {accessToken}
```
**Response 200**
```json
{
  "success": true,
  "data": [
    {
      "reservationId": 5001, "concertId": 1, "status": "CONFIRMED", "lockStrategy": "REDIS",
      "holdExpiresAt": "2026-08-01T10:05:00",
      "seats": [ { "seatId": 101, "priceSnapshot": 200000 } ],
      "payment": { "status": "PAID", "amount": 200000, "paidAt": "2026-08-01T10:03:00" }
    }
  ]
}
```
결제 전(HOLDING/EXPIRED 등) 예약은 `payment`가 `null`이다.

### 4.4 예약 취소
```
DELETE /api/v1/reservations/{reservationId}
Header: Authorization: Bearer {accessToken}
```
본인 소유의 `CONFIRMED` 예약만 취소 가능하며, 취소 시 좌석은 `AVAILABLE`로, 발급된 티켓은 `CANCELLED`로 함께 반환된다 (UC-11, `ReservationCancelService`).
**Response 200**: `{ "success": true, "data": { "status": "CANCELLED" }, "error": null }`
**Errors**: `404 RESERVATION_NOT_FOUND`(존재하지 않거나 `CONFIRMED` 상태가 아닌 예약), `403 NOT_RESERVATION_OWNER`, `422 CANCELLATION_DEADLINE_PASSED`(공연 D-1 이내)

## 5. 결제 (Payment)

### 5.1 결제 요청 (Mock)
```
POST /api/v1/payments
Header: Authorization: Bearer {accessToken}
```
**Request Body**
```json
{ "reservationId": 5001, "method": "MOCK" }
```
**Response 200 (성공)**
```json
{
  "success": true,
  "data": { "paymentId": 9001, "status": "PAID", "amount": 400000, "pgTransactionId": "mock-tx-abc", "paidAt": "2026-08-01T10:03:00" }
}
```
**Response 200 (실패, Mock 시뮬레이션)**
```json
{ "success": false, "data": null, "error": { "code": "PAYMENT_FAILED", "message": "Mock PG 결제 실패" } }
```
**Errors**: `404 RESERVATION_NOT_FOUND`, `409 RESERVATION_EXPIRED`, `409 RESERVATION_ALREADY_PAID`

## 6. 티켓 (Ticket)

### 6.1 내 티켓 목록 조회
```
GET /api/v1/tickets
```
**Response 200**
```json
{
  "success": true,
  "data": [
    { "ticketId": 7001, "ticketNumber": "b3f1...", "qrCodeValue": "...", "status": "ISSUED", "issuedAt": "2026-08-01T10:03:00" }
  ]
}
```
`seatNumber`/`concertTitle` 등 표시용 조인 필드는 아직 반환하지 않는다(`TicketService.findMyTickets` 주석 참고) — 필요 시 `GET /api/v1/reservations`(4.3)의 `seats[].seatId`로 좌석 정보를 별도 조회해 조합해야 한다.

## 7. 관리자 (Admin)

모든 관리자 API는 `Authorization` 헤더의 사용자가 `ROLE_ADMIN`이어야 하며, 그렇지 않으면 `403 ACCESS_DENIED`를 반환한다.

### 7.1 콘서트 등록
```
POST /api/v1/admin/concerts
```
**Request Body**: title, description, venue, performanceAt, bookingOpenAt, bookingCloseAt, posterImageUrl

### 7.2 좌석 등급 등록
```
POST /api/v1/admin/concerts/{concertId}/seat-grades
```
**Request Body**: `[ { "gradeName": "VIP", "price": 200000, "totalCount": 100 } ]`

### 7.3 좌석 일괄 생성
```
POST /api/v1/admin/concerts/{concertId}/seats/bulk
```
**Request Body**: `{ "seatGradeId": 1, "seatNumbers": ["A-1", "A-2", "..."] }`

## 8. 공통 에러 응답 포맷

```json
{ "success": false, "data": null, "error": { "code": "SEAT_ALREADY_HELD", "message": "이미 선점된 좌석입니다." } }
```

전체 에러 코드 목록은 [error-codes.md](./error-codes.md) 참고.

## 9. 페이지네이션 규칙

목록 조회 API는 Spring Data `Pageable` 규칙(`page`, `size`, `sort` 쿼리 파라미터)을 따르며, 응답은 `{ content, page, size, totalElements }` 형태로 감싼다.
