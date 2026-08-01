package starters.springboot.claude.starterkit.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc OpenAPI 설정 (docs/architecture.md D-1, docs/tech-decisions.md 참고).
 */
@Configuration
public class OpenApiConfig {

    private static final String JWT_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(apiInfo())
                .tags(tags())
                .components(new Components().addSecuritySchemes(JWT_SCHEME_NAME, jwtSecurityScheme()))
                .addSecurityItem(new SecurityRequirement().addList(JWT_SCHEME_NAME));
    }

    private Info apiInfo() {
        return new Info()
                .title("콘서트 티켓팅 Starter-Kit API")
                .description("""
                        NOL 유니버스/인터파크 티켓과 유사한 콘서트 예매 백엔드 starter-kit입니다.
                        좌석 선점 동시성 제어를 Redis 분산락(`/reservations/redis-lock`)과 \
                        DB 비관적 락(`/reservations/pessimistic-lock`) 두 가지 전략으로 나란히 제공하여 \
                        트레이드오프를 비교 학습할 수 있도록 설계했습니다 (docs/tech-decisions.md D-2 참고).
                        """)
                .version("v1");
    }

    private List<Tag> tags() {
        return List.of(
                new Tag().name("Auth").description("회원가입/로그인 (JWT 발급)"),
                new Tag().name("Concert").description("콘서트/좌석 조회"),
                new Tag().name("Queue").description("대기열 진입/상태 조회"),
                new Tag().name("Reservation").description("좌석 선점/예약/취소 — 두 동시성 전략 비교"),
                new Tag().name("Payment").description("Mock 결제"),
                new Tag().name("Ticket").description("본인 티켓 조회"),
                new Tag().name("Admin").description("관리자 전용 콘서트/좌석 관리")
        );
    }

    private SecurityScheme jwtSecurityScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization");
    }
}
