package starters.springboot.claude.starterkit.queue.dto;

import java.time.LocalDateTime;

public record QueueStatusResponse(
        String status,
        Long rank,
        Long estimatedWaitSeconds,
        LocalDateTime admittedTokenExpiresAt
) {

    public static QueueStatusResponse waiting(long rank, long estimatedWaitSeconds) {
        return new QueueStatusResponse("WAITING", rank, estimatedWaitSeconds, null);
    }

    public static QueueStatusResponse admitted(LocalDateTime expiresAt) {
        return new QueueStatusResponse("ADMITTED", null, null, expiresAt);
    }
}
