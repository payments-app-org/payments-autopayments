package com.paymentsapp.autopayments.web;

import com.paymentsapp.autopayments.template.Template;
import com.paymentsapp.autopayments.template.TemplateService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Шаблоны платежей пользователя (X-User-Id — пока без авторизации, как в остальных сервисах)
@RestController
@RequestMapping("/api/autopayments/templates")
public class TemplateController {

    private final TemplateService templates;

    public TemplateController(TemplateService templates) {
        this.templates = templates;
    }

    @GetMapping
    public List<Template> list(@RequestHeader(Headers.USER_ID) UUID userId) {
        return templates.list(userId);
    }

    @GetMapping("/{id}")
    public Template get(@RequestHeader(Headers.USER_ID) UUID userId, @PathVariable UUID id) {
        return templates.get(userId, id);
    }

    @PostMapping
    public Template create(@RequestHeader(Headers.USER_ID) UUID userId, @RequestBody TemplateService.Request req) {
        return templates.create(userId, req);
    }

    @PutMapping("/{id}")
    public Template update(@RequestHeader(Headers.USER_ID) UUID userId, @PathVariable UUID id, @RequestBody TemplateService.Request req) {
        return templates.update(userId, id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@RequestHeader(Headers.USER_ID) UUID userId, @PathVariable UUID id) {
        templates.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
