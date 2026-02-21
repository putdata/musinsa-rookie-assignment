package com.musinsa.api.queue;

import com.musinsa.api.queue.dtos.QueueDtos;
import com.musinsa.service.queue.WaitingQueueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
@Profile("redis")
public class QueueController {

    private final WaitingQueueService waitingQueueService;

    @PostMapping("/enroll")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public QueueDtos.EnrollResponse enroll(@Valid @RequestBody QueueDtos.EnrollRequest request) {
        return waitingQueueService.enqueue(request.studentId(), request.courseId());
    }

    @GetMapping("/result/{token}")
    public QueueDtos.ResultResponse getResult(@PathVariable String token) {
        return waitingQueueService.getResult(token);
    }
}
