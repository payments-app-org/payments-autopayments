package com.paymentsapp.autopayments.web;

import com.paymentsapp.autopayments.topup.Topup;
import com.paymentsapp.autopayments.topup.TopupService;
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

// Автопополнения своих счетов: раз в месяц, по балансу, постоянный баланс
@RestController
@RequestMapping("/api/autopayments/topups")
public class TopupController {

    private final TopupService topups;

    public TopupController(TopupService topups) {
        this.topups = topups;
    }

    @GetMapping
    public List<Topup> list(@RequestHeader("X-User-Id") UUID userId) {
        return topups.list(userId);
    }

    @GetMapping("/{id}")
    public Topup.WithRuns get(@RequestHeader("X-User-Id") UUID userId, @PathVariable UUID id) {
        return topups.get(userId, id);
    }

    @PostMapping
    public Topup create(@RequestHeader("X-User-Id") UUID userId, @RequestBody TopupService.Request req) {
        return topups.create(userId, req);
    }

    @PatchMapping("/{id}")
    public Topup status(@RequestHeader("X-User-Id") UUID userId, @PathVariable UUID id, @RequestBody Map<String, String> body) {
        return topups.setStatus(userId, id, body.get("status"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@RequestHeader("X-User-Id") UUID userId, @PathVariable UUID id) {
        topups.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
