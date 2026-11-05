package com.paymentsapp.autopayments.client;

import static com.paymentsapp.autopayments.error.AutopaymentsError.BILL_INVALID;
import static com.paymentsapp.autopayments.error.AutopaymentsError.PROVIDER_NOT_FOUND;

import com.paymentsapp.autopayments.client.BillerApi.BillCheckRequest;
import com.paymentsapp.platform.error.DomainException;
import com.paymentsapp.platform.resilience.ResilientCalls;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.json.JsonMapper;

// biller: проверка платежа поставщику теми же правилами, что и при обычной оплате, и карточка поставщика
@Component
public class BillerClient {

    private final BillerApi api;
    private final ResilientCalls calls;
    private final JsonMapper json;

    public BillerClient(BillerApi api, ResilientCalls calls, JsonMapper json) {
        this.api = api;
        this.calls = calls;
        this.json = json;
    }

    public BillCheck check(String providerCode, Map<String, String> fields, BigDecimal amount) {
        try {
            return calls.call(Backends.BILLER, () -> api.check(new BillCheckRequest(providerCode, fields, amount)));
        } catch (HttpClientErrorException e) {
            throw Rejections.of(BILL_INVALID, e, json);
        }
    }

    public Provider provider(String code) {
        try {
            return calls.call(Backends.BILLER, () -> api.provider(code)).provider();
        } catch (HttpClientErrorException.NotFound e) {
            throw new DomainException(PROVIDER_NOT_FOUND);
        }
    }
}
