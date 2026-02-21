package com.musinsa.api.queue.dtos;

import jakarta.validation.constraints.NotNull;

public class QueueDtos {

    public record EnrollRequest(
            @NotNull Long studentId,
            @NotNull Long courseId
    ) {}

    public record EnrollResponse(String token, long position, long totalWaiting) {}

    public record ResultResponse(String token, String status, String message) {}
}
