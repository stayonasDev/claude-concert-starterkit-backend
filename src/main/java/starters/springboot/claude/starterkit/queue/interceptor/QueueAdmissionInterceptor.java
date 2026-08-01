package starters.springboot.claude.starterkit.queue.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import starters.springboot.claude.starterkit.queue.service.QueueTokenService;
import starters.springboot.claude.starterkit.queue.service.QueueTokenService.AdmissionCheckResult;

/**
 * 대기열 활성화(app.queue.enabled) 시, 예약 API 호출 전에 입장권을 검증한다
 * (docs/architecture.md 4장, docs/use-cases.md UC-06 사전조건).
 *
 * 예약 요청 바디(JSON)에 있는 concertId를 body-buffering 없이 읽을 수 없으므로,
 * 이 인터셉터는 클라이언트가 별도로 보내는 X-Concert-Id/X-Queue-Token 헤더로 검증한다.
 * 기본값(app.queue.enabled=false)에서는 아무 헤더 없이도 통과하여, 대기열 없이 핵심
 * 예약 플로우만 단독으로 학습/테스트할 수 있다.
 */
@Component
@RequiredArgsConstructor
public class QueueAdmissionInterceptor implements HandlerInterceptor {

    private static final String QUEUE_REQUIRED_BODY =
            "{\"success\":false,\"data\":null,\"error\":{\"code\":\"QUEUE_REQUIRED\",\"message\":\"대기열 입장이 필요합니다.\"}}";
    private static final String QUEUE_TOKEN_EXPIRED_BODY =
            "{\"success\":false,\"data\":null,\"error\":{\"code\":\"QUEUE_TOKEN_EXPIRED\",\"message\":\"입장권이 만료되었습니다. 대기열에 다시 진입해주세요.\"}}";

    private final QueueTokenService queueTokenService;

    @Value("${app.queue.enabled:false}")
    private boolean queueEnabled;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!queueEnabled) {
            return true;
        }

        String concertIdHeader = request.getHeader("X-Concert-Id");
        String token = request.getHeader("X-Queue-Token");

        if (concertIdHeader == null || token == null) {
            respond(response, HttpStatus.FORBIDDEN, QUEUE_REQUIRED_BODY);
            return false;
        }

        AdmissionCheckResult result = checkAdmission(concertIdHeader, token);
        if (result == AdmissionCheckResult.ADMITTED) {
            return true;
        }
        String body = result == AdmissionCheckResult.EXPIRED ? QUEUE_TOKEN_EXPIRED_BODY : QUEUE_REQUIRED_BODY;
        respond(response, HttpStatus.FORBIDDEN, body);
        return false;
    }

    private AdmissionCheckResult checkAdmission(String concertIdHeader, String token) {
        try {
            return queueTokenService.checkAdmission(Long.valueOf(concertIdHeader), token);
        } catch (NumberFormatException e) {
            return AdmissionCheckResult.NOT_ADMITTED;
        }
    }

    private void respond(HttpServletResponse response, HttpStatus status, String body) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(body);
    }
}
