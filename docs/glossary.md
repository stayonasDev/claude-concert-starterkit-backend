# 용어집 (Glossary)

> 프로젝트 전반에서 일관되게 사용하는 도메인 용어를 정의한다. 코드의 클래스/변수명과 이 용어집의 한글 용어를 1:1로 매핑하여 사용한다.

| 용어 | 영문/코드명 | 정의 |
|---|---|---|
| 콘서트 | `Concert` | 예매 대상이 되는 공연 1건 (1 Concert = 1 공연 일시) |
| 좌석 등급 | `SeatGrade` | 콘서트 내 가격 구분 단위 (VIP/R/S/A 등) |
| 좌석 | `Seat` | 예매 가능한 최소 단위. 상태(AVAILABLE/HELD/RESERVED)를 가짐 |
| 선점 / 홀드 | `HOLD` | 좌석을 임시로 점유하여 다른 사용자가 선택할 수 없게 만드는 상태. 일정 시간(기본 5분) 내 결제하지 않으면 자동 해제됨 |
| 예약 | `Reservation` | 사용자의 예매 시도 단위(헤더). 여러 좌석을 하나의 예약으로 묶을 수 있음 |
| 예약 좌석 | `ReservationSeat` | 예약에 포함된 개별 좌석 라인아이템 |
| 대기열 | `Waiting Queue` | 트래픽 폭주 시 사용자를 순번대로 대기시키는 시스템. Redis Sorted Set 기반 |
| 입장권 / 입장 토큰 | `Admission Token` | 대기열 순번 도달 시 발급되는, TTL이 있는 예약 페이지 접근 허가증 |
| 오픈런 | - | 예매 오픈 시각에 트래픽이 집중되는 상황 (NOL/인터파크 등에서 흔히 발생) |
| 동시성 제어 | `Concurrency Control` | 여러 요청이 동시에 같은 좌석에 접근할 때 정합성을 보장하는 메커니즘 |
| 분산락 | `Distributed Lock` | 여러 서버 인스턴스 간에도 유효한 락. 이 프로젝트에서는 Redisson `RLock` 사용 |
| 비관적 락 | `Pessimistic Lock` | 트랜잭션이 데이터를 읽는 시점에 락을 걸어 다른 트랜잭션의 접근을 차단하는 방식 (`SELECT ... FOR UPDATE`) |
| 낙관적 락 | `Optimistic Lock` | 버전 컬럼을 이용해 충돌을 사후 감지하는 방식. 이 프로젝트에서는 CAS 안전망 용도로만 `seats.version` 컬럼 예비 |
| 티켓 | `Ticket` | 결제 완료 후 좌석별로 발급되는 실물 증표 (QR 포함) |
| PG (Payment Gateway) | - | 결제 대행사. 이 프로젝트에서는 Mock으로 대체 |
| CAS | Compare-And-Swap | `WHERE` 절에 기존 값을 조건으로 넣어 원자적으로 갱신하는 기법 |
| SPOF | Single Point of Failure | 장애 시 전체 시스템에 영향을 주는 단일 실패 지점 |

관련 문서: [요구사항 명세서](./requirements.md) · [아키텍처 문서](./architecture.md)
