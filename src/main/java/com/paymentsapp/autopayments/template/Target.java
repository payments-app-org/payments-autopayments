package com.paymentsapp.autopayments.template;

import static com.paymentsapp.autopayments.error.AutopaymentsError.AMOUNT_REQUIRED;
import static com.paymentsapp.autopayments.error.AutopaymentsError.PROVIDER_REQUIRED;
import static com.paymentsapp.autopayments.error.AutopaymentsError.SOURCE_ACCOUNT_INVALID;

import com.paymentsapp.autopayments.client.Account;
import com.paymentsapp.autopayments.client.AccountClient;
import com.paymentsapp.autopayments.client.BillCheck;
import com.paymentsapp.autopayments.client.BillerClient;
import com.paymentsapp.autopayments.client.Provider;
import com.paymentsapp.platform.error.DomainException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

// Кому и сколько платим — общее у шаблона и автоплатежа. Проверяем, как настоящий платёж: biller — поля и сумму,
// account — что счета списания принадлежат пользователю и с них можно платить.
@Component
public class Target {

    // E-mail для чека — не то, кому платим: в списке показываем первое другое поле (телефон, номер карты, счёта)
    private static final String RECEIPT_EMAIL_FIELD = "email";

    private final AccountClient accounts;
    private final BillerClient biller;

    public Target(AccountClient accounts, BillerClient biller) {
        this.accounts = accounts;
        this.biller = biller;
    }

    public Checked check(UUID userId, String providerCode, Map<String, String> fields, BigDecimal amount,
                         List<UUID> sourceAccounts) {
        if (providerCode == null || providerCode.isBlank()) {
            throw new DomainException(PROVIDER_REQUIRED);
        }
        if (amount == null || amount.signum() <= 0) {
            throw new DomainException(AMOUNT_REQUIRED);
        }
        List<Account> own = accounts.accounts(userId);
        for (UUID id : sourceAccounts) {
            if (id == null || own.stream().noneMatch(a -> a.id().equals(id) && a.usable())) {
                throw new DomainException(SOURCE_ACCOUNT_INVALID);
            }
        }
        BillCheck bill = biller.check(providerCode, fields == null ? Map.of() : fields, amount);
        Provider provider = biller.provider(providerCode);
        Map<String, String> values = new LinkedHashMap<>();
        bill.fields().forEach(f -> values.put(f.key(), f.value()));
        String target = bill.fields().stream().filter(f -> !RECEIPT_EMAIL_FIELD.equals(f.key()))
                .map(BillCheck.Field::display).findFirst().orElse(null);
        return new Checked(provider, values, target);
    }

    public record Checked(Provider provider, Map<String, String> fields, String target) {
    }
}
