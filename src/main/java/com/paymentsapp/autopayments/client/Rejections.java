package com.paymentsapp.autopayments.client;

import com.paymentsapp.autopayments.error.AutopaymentsError;
import com.paymentsapp.platform.error.DomainException;
import com.paymentsapp.platform.error.RemoteProblem;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.json.JsonMapper;

// Отказ соседа (4xx) — пользователю его текстом; текста нет — шаблон кода
final class Rejections {

    private Rejections() {
    }

    static String detail(HttpClientErrorException e, JsonMapper json, AutopaymentsError fallback) {
        return RemoteProblem.of(e, json, fallback.template()).detail();
    }

    static DomainException of(AutopaymentsError code, HttpClientErrorException e, JsonMapper json) {
        return DomainException.withMessage(code, detail(e, json, code));
    }
}
