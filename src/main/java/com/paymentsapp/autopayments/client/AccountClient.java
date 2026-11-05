package com.paymentsapp.autopayments.client;

import static com.paymentsapp.autopayments.error.AutopaymentsError.SBP_BANK_INVALID;

import com.paymentsapp.autopayments.client.AccountApi.SbpSourceRequest;
import com.paymentsapp.platform.resilience.ResilientCalls;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.json.JsonMapper;

// account: счета пользователя и счёт-источник для пополнения по СБП. Сбой связи — RestClientException.
@Component
public class AccountClient {

    private final AccountApi api;
    private final ResilientCalls calls;
    private final JsonMapper json;

    public AccountClient(AccountApi api, ResilientCalls calls, JsonMapper json) {
        this.api = api;
        this.calls = calls;
        this.json = json;
    }

    public List<Account> accounts(UUID userId) {
        return calls.call(Backends.ACCOUNT, () -> api.accounts(userId));
    }

    public Account sbpSource(UUID userId, String bankId, String bankName) {
        try {
            return calls.call(Backends.ACCOUNT, () -> api.sbpSource(userId, new SbpSourceRequest(bankId, bankName)));
        } catch (HttpClientErrorException e) {
            throw Rejections.of(SBP_BANK_INVALID, e, json);
        }
    }
}
