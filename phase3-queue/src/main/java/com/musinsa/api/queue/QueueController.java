package com.musinsa.api.queue;

import com.musinsa.api.queue.dtos.QueueDtos;
import com.musinsa.service.queue.WaitingQueueService;
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

    @PostMapping("/enter")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public QueueDtos.EnterResponse enter(@RequestParam Long studentId) {
        return waitingQueueService.enter(studentId);
    }

    @GetMapping("/status/{token}")
    public QueueDtos.StatusResponse getStatus(@PathVariable String token) {
        return waitingQueueService.getStatus(token);
    }
}
