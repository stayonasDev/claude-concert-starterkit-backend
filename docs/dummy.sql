-- ============================================================
-- 콘서트 티켓팅 Starter-Kit — 더미(시드) 데이터
-- 관련 문서: docs/database-schema.sql
--
-- docker-compose.yml에서 database-schema.sql 다음 순서(02-dummy.sql)로
-- docker-entrypoint-initdb.d에 마운트되어, MySQL 컨테이너를 최초 기동할 때
-- (데이터 볼륨이 비어 있을 때) 스키마 생성 직후 1회만 자동 실행된다.
-- 이미 데이터가 있는 볼륨을 재사용 중이라면 실행되지 않으므로, 새로 시드하려면
-- `docker compose down -v`로 볼륨을 지운 뒤 `docker compose up -d`로 다시 기동한다.
--
-- 모든 계정의 비밀번호는 "Password123!" 이다 (BCrypt로 해시되어 저장되어 있음).
-- 프론트엔드 로그인 화면이나 curl로 아래 계정을 바로 사용할 수 있다.
--   일반 사용자: dummy01@example.com ~ dummy10@example.com
--   관리자     : admin@example.com
-- ============================================================

-- ------------------------------------------------------------
-- users: 가상 사용자 10명 + 관리자 1명
-- ------------------------------------------------------------
INSERT INTO users (email, password, name, phone, role) VALUES
('dummy01@example.com', '$2a$10$GX164sscf0PF4zR2YrXB0OObRPtfy9sbBYKjwNtymHNNjiisbneFS', '더미유저01', '010-1000-0001', 'USER'),
('dummy02@example.com', '$2a$10$YRDaxSzCf70QK6XSHmDVSe80F2Yet7AtHJyYmOgfTKQxFha/d.uDi', '더미유저02', '010-1000-0002', 'USER'),
('dummy03@example.com', '$2a$10$WbwEYET1PAwjM9eglNZWW.zDPEFli6yZKjoSQ8b7O9r8wDN4m9/GG', '더미유저03', '010-1000-0003', 'USER'),
('dummy04@example.com', '$2a$10$3J4BNpXFbl.W9gRXnePhOOC6X4CGqmnveguvIMMPSKp2yI9.fO8GS', '더미유저04', '010-1000-0004', 'USER'),
('dummy05@example.com', '$2a$10$SAGPkwdtNCG2MXNX9uK4bOXfZlh.xfa3vFVKaRP5cPQa9Ie5wiVI.', '더미유저05', '010-1000-0005', 'USER'),
('dummy06@example.com', '$2a$10$PaMgq.E0vBBVKnIMfUIrzeSTihL9tpMn7o9nensi0cKsWFcMAgKxe', '더미유저06', '010-1000-0006', 'USER'),
('dummy07@example.com', '$2a$10$R5lHw4vZMmteslYRcQiGk.yT/271g3TYHX0RqmZXXAqUP0lvlebsS', '더미유저07', '010-1000-0007', 'USER'),
('dummy08@example.com', '$2a$10$zU/lztcuBWe/x6jeBJXWpejtOcsZ199vNDFjBTrRBoj1pEcnQRXOS', '더미유저08', '010-1000-0008', 'USER'),
('dummy09@example.com', '$2a$10$JbvGY5F3xPNzwN69FX0zYOpgtLKuucA5JTrSl2E.ZQIWFhNsndlpy', '더미유저09', '010-1000-0009', 'USER'),
('dummy10@example.com', '$2a$10$Z48fE.G.yRGVXNqUU30WVe0kcYFNBKR9YorcbV1/aiwpnVk1rb2qG', '더미유저10', '010-1000-0010', 'USER'),
('admin@example.com',   '$2a$10$D5O31QGtTXy5l/8aoE3d5.ylMh0PgpGNKeUiqbHHFacg1iLhl0tcK', '관리자',    '010-9000-0001', 'ADMIN');

-- ------------------------------------------------------------
-- concerts: 예매 상태별 시나리오를 확인할 수 있도록 상태를 다르게 구성한 콘서트 3건
-- 날짜는 NOW() 기준 상대값이므로, 시드가 실행되는 시점과 무관하게 항상
-- 의미 있는 상태(예매중/오픈예정/마감)를 유지한다.
-- ------------------------------------------------------------
INSERT INTO concerts (title, description, venue, performance_at, booking_open_at, booking_close_at, status) VALUES
('데뷔 10주년 기념 콘서트', '동시성 데모·대기열 체험에 쓰기 좋은 예매중 콘서트', '잠실 올림픽공원 체조경기장',
    DATE_ADD(NOW(), INTERVAL 30 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_ADD(NOW(), INTERVAL 29 DAY), 'ON_SALE'),
('가을밤 재즈 페스티벌', '예매 오픈 전 상태 확인용 콘서트', '올림픽공원 88잔디마당',
    DATE_ADD(NOW(), INTERVAL 60 DAY), DATE_ADD(NOW(), INTERVAL 20 DAY), DATE_ADD(NOW(), INTERVAL 59 DAY), 'UPCOMING'),
('겨울 팝업 락 페스티벌', '예매 마감 상태 확인용 콘서트', '고척스카이돔',
    DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 40 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY), 'CLOSED');

-- ------------------------------------------------------------
-- seat_grades: 콘서트별 좌석 등급(가격 정책)
-- ------------------------------------------------------------
INSERT INTO seat_grades (concert_id, grade_name, price, total_count)
SELECT id, 'VIP', 150000, 10 FROM concerts WHERE title = '데뷔 10주년 기념 콘서트'
UNION ALL
SELECT id, 'R', 120000, 20 FROM concerts WHERE title = '데뷔 10주년 기념 콘서트'
UNION ALL
SELECT id, 'S', 90000, 30 FROM concerts WHERE title = '데뷔 10주년 기념 콘서트'
UNION ALL
SELECT id, 'VIP', 130000, 8 FROM concerts WHERE title = '가을밤 재즈 페스티벌'
UNION ALL
SELECT id, 'R', 100000, 16 FROM concerts WHERE title = '가을밤 재즈 페스티벌'
UNION ALL
SELECT id, 'VIP', 140000, 10 FROM concerts WHERE title = '겨울 팝업 락 페스티벌'
UNION ALL
SELECT id, 'R', 110000, 20 FROM concerts WHERE title = '겨울 팝업 락 페스티벌';

-- ------------------------------------------------------------
-- seats: 등급별 total_count 만큼 "등급명-번호" 형식으로 좌석을 생성한다.
-- 재귀 CTE로 1~30 시퀀스를 만들고 등급의 total_count 이하인 번호만 좌석으로 만든다
-- (가장 큰 등급이 30석이므로 시퀀스 상한은 30이다. 등급별 총좌석수를 늘리면 이 상한도 맞춰 늘려야 한다).
-- ------------------------------------------------------------
INSERT INTO seats (concert_id, seat_grade_id, seat_number, status)
SELECT sg.concert_id, sg.id, CONCAT(sg.grade_name, '-', seq.n), 'AVAILABLE'
FROM seat_grades sg
JOIN (
    WITH RECURSIVE seat_seq AS (
        SELECT 1 AS n
        UNION ALL
        SELECT n + 1 FROM seat_seq WHERE n < 30
    )
    SELECT n FROM seat_seq
) AS seq ON seq.n <= sg.total_count;
