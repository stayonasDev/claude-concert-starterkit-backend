package starters.springboot.claude.starterkit.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 전체 에러 코드 체계는 docs/error-codes.md 참고.
 * 여기에는 현재 구현된 기능(좌석 선점, 예약 오케스트레이션)에서 실제로 사용하는 코드만 정의한다.
 */
@Getter
public enum ErrorCode {

    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증 토큰이 없거나 유효하지 않습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    SEAT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 좌석입니다."),
    SEAT_ALREADY_HELD(HttpStatus.CONFLICT, "이미 선점되었거나 예약된 좌석입니다."),
    CONCERT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 콘서트입니다."),
    BOOKING_NOT_OPEN(HttpStatus.FORBIDDEN, "예매 가능한 기간이 아닙니다."),
    BOOKING_CLOSED(HttpStatus.FORBIDDEN, "예매 마감 시각이 지났습니다."),
    SEAT_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT, "한 번에 예약할 수 있는 좌석 수를 초과했습니다."),
    ACTIVE_RESERVATION_EXISTS(HttpStatus.CONFLICT, "이미 진행 중인 예약이 있습니다."),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 예약입니다."),
    NOT_RESERVATION_OWNER(HttpStatus.FORBIDDEN, "본인 소유가 아닌 예약입니다."),
    RESERVATION_EXPIRED(HttpStatus.CONFLICT, "선점이 만료되었거나 결제할 수 없는 예약 상태입니다."),
    RESERVATION_ALREADY_PAID(HttpStatus.CONFLICT, "이미 결제 완료된 예약입니다."),
    CANCELLATION_DEADLINE_PASSED(HttpStatus.UNPROCESSABLE_CONTENT, "공연 D-1 이내에는 예약을 취소할 수 없습니다."),
    // docs/error-codes.md: Mock PG가 실패를 반환한 경우. data 없이 error만 포함하되 HTTP 상태는 200으로 응답한다.
    PAYMENT_FAILED(HttpStatus.OK, "결제에 실패했습니다."),
    QUEUE_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "유효하지 않은 대기열 토큰입니다."),
    QUEUE_REQUIRED(HttpStatus.FORBIDDEN, "대기열 입장이 필요합니다."),
    QUEUE_TOKEN_EXPIRED(HttpStatus.FORBIDDEN, "입장권이 만료되었습니다. 대기열에 다시 진입해주세요."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
