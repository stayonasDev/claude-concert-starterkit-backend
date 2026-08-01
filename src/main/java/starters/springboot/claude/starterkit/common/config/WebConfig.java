package starters.springboot.claude.starterkit.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import starters.springboot.claude.starterkit.queue.interceptor.QueueAdmissionInterceptor;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final QueueAdmissionInterceptor queueAdmissionInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 좌석 선점(POST) 요청에만 대기열 입장권을 요구한다. 목록 조회(GET)/취소(DELETE)는
        // 이미 선점을 마친 사용자의 후속 조치이므로 대기열 검증 대상이 아니다.
        registry.addInterceptor(queueAdmissionInterceptor)
                .addPathPatterns("/api/v1/reservations/redis-lock", "/api/v1/reservations/pessimistic-lock");
    }
}
