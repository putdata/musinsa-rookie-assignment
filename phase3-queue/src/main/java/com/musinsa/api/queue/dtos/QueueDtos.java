package com.musinsa.api.queue.dtos;

public class QueueDtos {
    public record EnterResponse(String token, long position, long totalWaiting) {}
    public record StatusResponse(String token, long position, long totalWaiting, boolean allowed) {}
}
