package starters.springboot.claude.starterkit.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import starters.springboot.claude.starterkit.user.domain.Role;
import starters.springboot.claude.starterkit.user.security.JwtTokenProvider;

/**
 * 통합/동시성 테스트가 공통으로 사용하는 MySQL·Redis 가상 컨테이너 설정
 * (docs/tech-decisions.md D-4, docs/ci-cd.md 참고).
 *
 * 컨테이너는 static 필드 + static 초기화 블록으로 JVM(테스트 프로세스)당 한 번만
 * 기동하여, 이 클래스를 extends하는 모든 테스트 클래스가 동일한 컨테이너를 공유한다.
 * 별도로 stop()을 호출하지 않아도 Testcontainers의 Ryuk 리소스 리퍼가 JVM 종료 시
 * 컨테이너를 정리한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public abstract class ContainerTestSupport {

    private static final MySQLContainer<?> MYSQL_CONTAINER =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                    .withDatabaseName("starterkit")
                    .withUsername("test")
                    .withPassword("test");

    private static final GenericContainer<?> REDIS_CONTAINER =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    static {
        MYSQL_CONTAINER.start();
        REDIS_CONTAINER.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL_CONTAINER::getUsername);
        registry.add("spring.datasource.password", MYSQL_CONTAINER::getPassword);
        // 참고용 DDL(docs/database-schema.sql)은 아직 마이그레이션 도구로 옮기지 않았으므로
        // 테스트 한정으로 Hibernate가 엔티티 기준 스키마를 생성하도록 한다.
        // MySQL 컨테이너는 JVM(테스트 프로세스) 전체에서 하나만 공유되는데, 서로 다른
        // @TestPropertySource 설정(예: QueueAdmissionInterceptorTest)마다 별도의 스프링
        // 컨텍스트가 뜨면서 각자 create-drop을 쓰면, 한 컨텍스트가 종료되며 테이블을 DROP할 때
        // 같은 컨테이너를 쓰는 다른(아직 살아있는) 컨텍스트의 @Scheduled 빈이 그 테이블을
        // 조회하다 "Table doesn't exist" 오류를 내는 경합이 발생한다. update로 바꿔 컨텍스트가
        // 종료되어도 스키마를 지우지 않도록 한다 — 컨테이너 자체는 JVM 종료 시 Ryuk이 정리한다.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");

        registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
        registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getMappedPort(6379));
    }

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    /** MockMvc 테스트에서 실제 로그인 흐름 없이 바로 쓸 수 있는 Authorization 헤더 값을 만든다. */
    protected String bearerToken(Long userId, Role role) {
        return "Bearer " + jwtTokenProvider.createToken(userId, role);
    }
}
