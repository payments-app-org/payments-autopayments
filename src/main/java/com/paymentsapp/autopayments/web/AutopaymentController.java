package com.paymentsapp.autopayments.web;

import com.paymentsapp.autopayments.autopay.Autopayment;
import com.paymentsapp.autopayments.autopay.AutopaymentService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Автоплатежи пользователя: создание, список, карточка с историей запусков, пауза, удаление
@RestController
@RequestMapping("/api/autopayments")
public class AutopaymentController {

    private final AutopaymentService autopayments;

    public AutopaymentController(AutopaymentService autopayments) {
        this.autopayments = autopayments;
    }

    @GetMapping
    public List<Autopayment> list(@RequestHeader("X-User-Id") UUID userId) {
        return autopayments.list(userId);
    }

    @GetMapping("/{id}")
    public Autopayment.WithRuns get(@RequestHeader("X-User-Id") UUID userId, @PathVariable UUID id) {
        return autopayments.get(userId, id);
    }

    @PostMapping
    public Autopayment create(@RequestHeader("X-User-Id") UUID userId, @RequestBody AutopaymentService.Request req) {
        return autopayments.create(userId, req);
    }

    // {"status": "PAUSED" | "ACTIVE"}
    @PatchMapping("/{id}")
    public Autopayment status(@RequestHeader("X-User-Id") UUID userId, @PathVariable UUID id, @RequestBody Map<String, String> body) {
        return autopayments.setStatus(userId, id, body.get("status"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@RequestHeader("X-User-Id") UUID userId, @PathVariable UUID id) {
        autopayments.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
