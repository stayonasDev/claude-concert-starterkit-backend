package starters.springboot.claude.starterkit.reservation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import starters.springboot.claude.starterkit.common.lock.LockStrategyType;
import starters.springboot.claude.starterkit.common.response.ApiResponse;
import starters.springboot.claude.starterkit.reservation.dto.ReservationCancelResponse;
import starters.springboot.claude.starterkit.reservation.dto.ReservationCreateRequest;
import starters.springboot.claude.starterkit.reservation.dto.ReservationSummaryResponse;
import starters.springboot.claude.starterkit.reservation.service.ReservationCancelService;
import starters.springboot.claude.starterkit.reservation.service.ReservationFacade;
import starters.springboot.claude.starterkit.reservation.service.ReservationQueryService;
import starters.springboot.claude.starterkit.reservation.service.ReservationResult;
import starters.springboot.claude.starterkit.reservation.service.ReserveSeatsCommand;
import starters.springboot.claude.starterkit.user.security.AuthenticatedUser;

/**
 * 두 엔드포인트는 요청/응답 스펙이 동일하고 동시성 제어 전략만 다르다
 * (docs/architecture.md 3장, docs/api-spec.md 4장).
 */
@Tag(name = "Reservation")
@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationFacade reservationFacade;
    private final ReservationCancelService reservationCancelService;
    private final ReservationQueryService reservationQueryService;

    @Operation(summary = "본인 예약 목록 조회", description = "로그인한 사용자의 전체 예약 내역을 상태/좌석/결제 요약과 함께 조회합니다 (docs/use-cases.md UC-10).")
    @GetMapping
    public ApiResponse<List<ReservationSummaryResponse>> getMyReservations(Authentication authentication) {
        Long userId = AuthenticatedUser.from(authentication).id();
        return ApiResponse.success(reservationQueryService.findMyReservations(userId));
    }

    @Operation(
            summary = "좌석 선점 - Redis 분산락",
            description = "Redisson RLock으로 좌석을 선점합니다. 인메모리 락이라 처리량이 높고 대규모 동시 접속(오픈런)에 " +
                    "유리하지만, 락과 DB 반영이 분리되어 있어 부분 실패 보정 로직이 필요하고 Redis 장애가 새 SPOF가 됩니다 " +
                    "(docs/tech-decisions.md 2장 비교표 참고).")
    @PostMapping("/redis-lock")
    public ResponseEntity<ApiResponse<ReservationResult>> reserveWithRedisLock(
            Authentication authentication,
            @Valid @RequestBody ReservationCreateRequest request) {
        return reserve(authentication, request, LockStrategyType.REDIS);
    }

    @Operation(
            summary = "좌석 선점 - DB 비관적 락",
            description = "SELECT ... FOR UPDATE(PESSIMISTIC_WRITE)로 좌석을 선점합니다. 별도 인프라 없이 트랜잭션 " +
                    "경계 안에서 원자적으로 처리되어 정합성 보정이 불필요하지만, 락 보유 동안 DB 커넥션을 점유해 " +
                    "동시 요청이 많을수록 처리량이 급락합니다 (docs/tech-decisions.md 2장 비교표 참고).")
    @PostMapping("/pessimistic-lock")
    public ResponseEntity<ApiResponse<ReservationResult>> reserveWithPessimisticLock(
            Authentication authentication,
            @Valid @RequestBody ReservationCreateRequest request) {
        return reserve(authentication, request, LockStrategyType.PESSIMISTIC);
    }

    private ResponseEntity<ApiResponse<ReservationResult>> reserve(
            Authentication authentication, ReservationCreateRequest request, LockStrategyType lockStrategyType) {
        Long userId = AuthenticatedUser.from(authentication).id();
        ReserveSeatsCommand command = new ReserveSeatsCommand(
                userId, request.concertId(), request.seatIds(), lockStrategyType);
        ReservationResult result = reservationFacade.reserve(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result));
    }

    @Operation(summary = "예약 취소", description = "본인 소유의 CONFIRMED 예약을 공연 D-1 이전까지 취소합니다 (docs/use-cases.md UC-11).")
    @DeleteMapping("/{reservationId}")
    public ApiResponse<ReservationCancelResponse> cancel(
            Authentication authentication, @PathVariable Long reservationId) {
        Long userId = AuthenticatedUser.from(authentication).id();
        var status = reservationCancelService.cancel(reservationId, userId);
        return ApiResponse.success(ReservationCancelResponse.of(status));
    }
}
