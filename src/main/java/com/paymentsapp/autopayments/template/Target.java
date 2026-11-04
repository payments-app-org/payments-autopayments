package com.paymentsapp.autopayments.template;

import com.paymentsapp.autopayments.client.Clients;
import com.paymentsapp.autopayments.client.Clients.BillCheck;
import com.paymentsapp.autopayments.client.Clients.Provider;
import com.paymentsapp.autopayments.web.ApiException;
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

    private final Clients clients;

    public Target(Clients clients) {
        this.clients = clients;
    }

    public Checked check(UUID userId, String providerCode, Map<String, String> fields, BigDecimal amount, List<UUID> accounts) {
        if (providerCode == null || providerCode.isBlank()) {
            throw ApiException.badRequest("Не выбран поставщик");
        }
        if (amount == null || amount.signum() <= 0) {
            throw ApiException.badRequest("Укажите сумму платежа");
        }
        List<Clients.Account> own = clients.accounts(userId);
        for (UUID id : accounts) {
            if (id != null && own.stream().noneMatch(a -> a.id().equals(id) && a.usable())) {
                throw ApiException.badRequest("Счёт списания не найден — выберите кошелёк или карту нашего банка");
            }
        }
        BillCheck bill = clients.checkBill(providerCode, fields == null ? Map.of() : fields, amount);
        Provider p = clients.provider(providerCode);
        Map<String, String> values = new LinkedHashMap<>();
        bill.fields().forEach(f -> values.put(f.key(), f.value()));
        // Главное, что видно в списке: номер телефона, карты, счёта
        String target = bill.fields().stream().filter(f -> !f.key().equals("email")).map(Clients.BillField::display)
                .findFirst().orElse(null);
        return new Checked(p, values, target);
    }

    public record Checked(Provider provider, Map<String, String> fields, String target) {
    }
}
