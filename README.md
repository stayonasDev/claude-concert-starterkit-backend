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
