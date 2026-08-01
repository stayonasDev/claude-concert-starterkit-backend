-- ============================================================
-- 콘서트 티켓팅 Starter-Kit — 데이터베이스 스키마 (MySQL 8.x)
-- 관련 문서: docs/erd.md, docs/architecture.md
--
-- 참고: 실제 애플리케이션에서는 JPA(Hibernate ddl-auto) 또는
-- Flyway/Liquibase 마이그레이션으로 스키마를 관리하는 것을 권장한다.
-- 이 파일은 참조용 DDL 문서이며, 구현 단계에서 마이그레이션 도구로
-- 옮겨 담는 것을 전제로 한다.
-- ============================================================

-- ------------------------------------------------------------
-- users: 예매자/관리자 계정
-- ------------------------------------------------------------
CREATE TABLE users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    email         VARCHAR(255)   NOT NULL,
    password      VARCHAR(255)   NOT NULL,          -- BCrypt 등으로 암호화 저장
    name          VARCHAR(50)    NOT NULL,
    phone         VARCHAR(20),
    role          VARCHAR(20)    NOT NULL DEFAULT 'USER',  -- USER / ADMIN
    created_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME       NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
);

-- ------------------------------------------------------------
-- concerts: 공연 정보, 예매 오픈/마감 시각 관리
-- ------------------------------------------------------------
CREATE TABLE concerts (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    title             VARCHAR(200)  NOT NULL,
    description       TEXT,
    venue             VARCHAR(200)  NOT NULL,
    performance_at    DATETIME      NOT NULL,
    booking_open_at   DATETIME      NOT NULL,
    booking_close_at  DATETIME      NOT NULL,
    status            VARCHAR(20)   NOT NULL DEFAULT 'UPCOMING',  -- UPCOMING/ON_SALE/SOLD_OUT/CLOSED
    poster_image_url  VARCHAR(500),
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME      NULL,
    INDEX idx_concerts_status_performance_at (status, performance_at),
    INDEX idx_concerts_booking_open_at (booking_open_at)
);

-- ------------------------------------------------------------
-- seat_grades: 공연별 좌석 등급(가격 정책)
-- ------------------------------------------------------------
CREATE TABLE seat_grades (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    concert_id    BIGINT        NOT NULL,
    grade_name    VARCHAR(20)   NOT NULL,   -- VIP / R / S / A ...
    price         DECIMAL(10,0) NOT NULL,
    total_count   INT           NOT NULL,
    CONSTRAINT fk_seat_grades_concert FOREIGN KEY (concert_id) REFERENCES concerts(id),
    CONSTRAINT uk_seat_grades_concert_grade UNIQUE (concert_id, grade_name)
);

-- ------------------------------------------------------------
-- seats: 개별 좌석 상태 — 동시성 제어의 핵심 테이블
-- ------------------------------------------------------------
CREATE TABLE seats (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    concert_id        BIGINT        NOT NULL,
    seat_grade_id     BIGINT        NOT NULL,
    seat_number       VARCHAR(20)   NOT NULL,
    status            VARCHAR(20)   NOT NULL DEFAULT 'AVAILABLE',  -- AVAILABLE / HELD / RESERVED
    held_by_user_id   BIGINT        NULL,
    held_at           DATETIME      NULL,
    hold_expires_at   DATETIME      NULL,
    version           BIGINT        NOT NULL DEFAULT 0,   -- CAS 안전망(Redis 락 경로) / 낙관락 확장용
    CONSTRAINT fk_seats_concert FOREIGN KEY (concert_id) REFERENCES concerts(id),
    CONSTRAINT fk_seats_seat_grade FOREIGN KEY (seat_grade_id) REFERENCES seat_grades(id),
    CONSTRAINT uk_seats_concert_seat_number UNIQUE (concert_id, seat_number),
    INDEX idx_seats_concert_status (concert_id, status)
);

-- ------------------------------------------------------------
-- reservations: 예약 헤더 (사용자의 예매 시도 단위)
-- ------------------------------------------------------------
CREATE TABLE reservations (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT        NOT NULL,
    concert_id        BIGINT        NOT NULL,
    status            VARCHAR(20)   NOT NULL,   -- HOLDING/PENDING_PAYMENT/CONFIRMED/CANCELLED/EXPIRED
    lock_strategy     VARCHAR(20)   NULL,       -- REDIS / PESSIMISTIC (두 전략 비교 기록용)
    held_at           DATETIME      NULL,
    hold_expires_at   DATETIME      NOT NULL,
    confirmed_at      DATETIME      NULL,
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME      NULL,
    CONSTRAINT fk_reservations_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_reservations_concert FOREIGN KEY (concert_id) REFERENCES concerts(id),
    INDEX idx_reservations_user_id (user_id),
    -- 만료 배치(ReservationExpirationScheduler)의 스캔 쿼리 최적화용 복합 인덱스
    INDEX idx_reservations_status_hold_expires_at (status, hold_expires_at)
);

-- ------------------------------------------------------------
-- reservation_seats: 예약-좌석 라인아이템 (1건의 예약에 최대 N석)
-- ------------------------------------------------------------
CREATE TABLE reservation_seats (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_id  BIGINT        NOT NULL,
    seat_id         BIGINT        NOT NULL,
    price_snapshot  DECIMAL(10,0) NOT NULL,
    CONSTRAINT fk_reservation_seats_reservation FOREIGN KEY (reservation_id) REFERENCES reservations(id),
    CONSTRAINT fk_reservation_seats_seat FOREIGN KEY (seat_id) REFERENCES seats(id),
    -- 주의: seat_id에 유니크 제약을 걸지 않음. 취소/만료 이력도 유지해야 하므로
    -- "좌석당 유효 예약 1건" 규칙은 DB 제약이 아닌 seats.status 상태 머신으로 강제한다.
    INDEX idx_reservation_seats_seat_id (seat_id),
    INDEX idx_reservation_seats_reservation_id (reservation_id)
);

-- ------------------------------------------------------------
-- payments: Mock 결제 처리 결과
-- ------------------------------------------------------------
CREATE TABLE payments (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_id      BIGINT        NOT NULL,
    amount              DECIMAL(10,0) NOT NULL,
    method              VARCHAR(20)   NOT NULL,   -- CARD / SIMPLE_PAY / MOCK
    status              VARCHAR(20)   NOT NULL,   -- READY/PAID/FAILED/CANCELLED
    pg_transaction_id   VARCHAR(100)  NULL,
    paid_at             DATETIME      NULL,
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME      NULL,
    CONSTRAINT fk_payments_reservation FOREIGN KEY (reservation_id) REFERENCES reservations(id),
    CONSTRAINT uk_payments_reservation UNIQUE (reservation_id),  -- 1:1 관계 강제
    INDEX idx_payments_status (status)
);

-- ------------------------------------------------------------
-- tickets: 결제 완료 후 발급되는 실물 티켓
-- ------------------------------------------------------------
CREATE TABLE tickets (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_seat_id   BIGINT        NOT NULL,
    ticket_number         VARCHAR(36)   NOT NULL,   -- UUID
    qr_code_value         VARCHAR(500),
    status                VARCHAR(20)   NOT NULL,   -- ISSUED/USED/CANCELLED
    issued_at             DATETIME      NOT NULL,
    CONSTRAINT fk_tickets_reservation_seat FOREIGN KEY (reservation_seat_id) REFERENCES reservation_seats(id),
    CONSTRAINT uk_tickets_reservation_seat UNIQUE (reservation_seat_id),  -- 1:1 관계 강제
    CONSTRAINT uk_tickets_ticket_number UNIQUE (ticket_number)
);

-- ------------------------------------------------------------
-- queue_entry_logs: 대기열 분석/감사용 (선택적)
-- 대기열의 1차 저장소는 Redis Sorted Set이며, 이 테이블은
-- Redis TTL 만료 이후에도 통계가 필요할 때만 비동기로 적재한다.
-- ------------------------------------------------------------
CREATE TABLE queue_entry_logs (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    concert_id    BIGINT        NOT NULL,
    user_id       BIGINT        NOT NULL,
    token         VARCHAR(64)   NOT NULL,
    entered_at    DATETIME      NOT NULL,
    admitted_at   DATETIME      NULL,
    INDEX idx_queue_entry_logs_concert (concert_id)
);
