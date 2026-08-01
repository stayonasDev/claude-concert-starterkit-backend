package starters.springboot.claude.starterkit.queue.dto;

public record QueueEnterResponse(String token, long rank, long estimatedWaitSeconds) {
}
