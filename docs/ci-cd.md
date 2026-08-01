# CI/CD 워크플로우 문서

> 관련 문서: [테스트 시나리오](./test-scenarios.md) · [기술 선택 근거](./tech-decisions.md)
> 실제 워크플로우 정의: [`.github/workflows/ci.yml`](../.github/workflows/ci.yml)

## 1. 범위

이번 단계에서는 **빌드 + 테스트(CI)까지만** 포함하며, 배포(CD) 단계는 포함하지 않는다.

**근거**: 이 프로젝트는 실 서비스 배포가 아닌 학습/재사용 목적의 starter-kit이며, 현재 시점에 배포 대상 인프라(클라우드/온프레미스, 레지스트리, 오케스트레이션 방식)가 확정되지 않았다. 배포 대상이 정해지지 않은 채 CD 파이프라인을 먼저 만들면 실제로 쓰이지 않는 자리표시자(placeholder) 스텝만 남게 되어, 오히려 "돌아간다고 착각하게 만드는" 위험한 문서가 된다. 따라서 CD는 배포 대상이 확정된 이후 별도로 설계한다 ([tech-decisions.md](./tech-decisions.md#5-향후-검토-대상-not-decided) 참고).

## 2. 트리거 조건

| 이벤트 | 대상 브랜치 |
|---|---|
| `push` | `main`, `develop` |
| `pull_request` | `main`, `develop` |

## 3. 파이프라인 단계

```mermaid
graph LR
    A[소스 체크아웃] --> B[JDK 17 설정]
    B --> C[Gradle 캐시 설정]
    C --> D[빌드 - 테스트 제외]
    D --> E["테스트 실행<br/>(TestContainers 기반)"]
    E --> F[테스트 리포트 업로드]
```

1. **소스 체크아웃**: `actions/checkout@v4`
2. **JDK 17 설정**: `actions/setup-java@v4`, Temurin 배포판 — `build.gradle`의 toolchain 설정(Java 17)과 일치시킴
3. **Gradle 캐시 설정**: `gradle/actions/setup-gradle@v4`로 빌드 캐시를 자동 관리하여 빌드 시간 단축
4. **빌드(테스트 제외)**: `./gradlew build -x test`로 컴파일/패키징만 우선 검증 — 컴파일 오류를 테스트 실행 전에 빠르게 표면화
5. **테스트 실행**: `./gradlew test` — [test-scenarios.md](./test-scenarios.md)에 정의된 통합/동시성 테스트가 TestContainers로 MySQL·Redis 컨테이너를 직접 기동해 실행됨
6. **테스트 리포트 업로드**: 실패 시에도(`if: always()`) 결과를 아티팩트로 남겨 원인 분석 가능하게 함

## 4. TestContainers 실행 환경 관련 참고

GitHub Actions의 `ubuntu-latest` 러너는 Docker가 기본 설치되어 있어, `services:` 블록으로 MySQL/Redis 컨테이너를 미리 띄우는 대신 TestContainers가 테스트 코드 실행 중 **필요한 시점에 직접** 컨테이너를 기동하고 종료한다. 이는 로컬 개발 환경(Docker Compose로 상시 기동)과 CI 환경(TestContainers로 테스트 시점에만 기동)이 서로 다른 방식이지만, 두 경우 모두 `spring.datasource.*`/`spring.data.redis.*` 설정 키를 통해 애플리케이션에 전달되므로 **애플리케이션 코드는 두 환경을 구분할 필요가 없다** ([tech-decisions.md](./tech-decisions.md#4-testcontainers--docker-compose-연동을-염두에-둔-설계-포인트) 참고).

## 5. 향후 확장 시 고려사항 (CD 설계 착수 시)

| 항목 | 확인 필요 사항 |
|---|---|
| 배포 대상 | 클라우드(AWS/GCP/Azure) vs 온프레미스, 컨테이너 오케스트레이션 여부 |
| 이미지 빌드 | Spring Boot의 OCI 이미지 빌드 기능(`bootBuildImage`) 또는 Dockerfile 직접 작성 |
| 배포 전략 | Blue-Green, Rolling, Canary 중 선택 |
| 시크릿 관리 | DB/Redis 접속 정보, 관리자 계정 등을 GitHub Actions Secrets 또는 외부 Secret Manager로 분리 |
| 승인 절차 | `main` 브랜치 배포 시 수동 승인(Environment protection rule) 필요 여부 |
