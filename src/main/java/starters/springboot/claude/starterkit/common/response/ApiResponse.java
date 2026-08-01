package starters.springboot.claude.starterkit.common.response;

import starters.springboot.claude.starterkit.common.exception.ErrorCode;

/**
 * 전체 API 공통 응답 포맷 (docs/api-spec.md 참고).
 */
public record ApiResponse<T>(boolean success, T data, ErrorDetail error) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return new ApiResponse<>(false, null, new ErrorDetail(errorCode.name(), errorCode.getMessage()));
    }

    public record ErrorDetail(String code, String message) {
    }
}
