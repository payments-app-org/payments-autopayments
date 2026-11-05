package com.paymentsapp.autopayments.template;

import static com.paymentsapp.autopayments.error.AutopaymentsError.TEMPLATE_NOT_FOUND;

import com.paymentsapp.autopayments.template.Template.ProviderView;
import com.paymentsapp.platform.error.DomainException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class TemplateRepository {

    private static final TypeReference<Map<String, String>> FIELDS = new TypeReference<>() { };

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public TemplateRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public List<Template> list(UUID userId) {
        return jdbc.sql("select * from templates where user_id = :user order by created_at")
                .param("user", userId).query(this::map).list();
    }

    public Template get(UUID userId, UUID id) {
        return jdbc.sql("select * from templates where id = :id and user_id = :user").param("id", id).param("user", userId)
                .query(this::map).optional().orElseThrow(() -> new DomainException(TEMPLATE_NOT_FOUND));
    }

    public void insert(UUID id, UUID userId, String name, Target.Checked target, BigDecimal amount, UUID sourceAccountId) {
        jdbc.sql("""
                        insert into templates (id, user_id, name, provider_code, provider_group, provider_name, provider_domain,
                            provider_color, provider_country, fields, target, amount, source_account_id)
                        values (:id, :user, :name, :code, :group, :pname, :domain, :color, :country, cast(:fields as jsonb),
                            :target, :amount, :source)
                        """)
                .param("id", id).param("user", userId).param("name", name)
                .param("code", target.provider().code()).param("group", target.provider().groupCode())
                .param("pname", target.provider().name()).param("domain", target.provider().domain())
                .param("color", target.provider().color()).param("country", target.provider().countryCode())
                .param("fields", json.writeValueAsString(target.fields())).param("target", target.target())
                .param("amount", amount).param("source", sourceAccountId)
                .update();
    }

    public void update(UUID id, String name, Target.Checked target, BigDecimal amount, UUID sourceAccountId) {
        jdbc.sql("""
                        update templates set name = :name, fields = cast(:fields as jsonb), target = :target, amount = :amount,
                            source_account_id = :source, updated_at = now()
                        where id = :id
                        """)
                .param("id", id).param("name", name).param("fields", json.writeValueAsString(target.fields()))
                .param("target", target.target()).param("amount", amount).param("source", sourceAccountId)
                .update();
    }

    public void delete(UUID id) {
        jdbc.sql("delete from templates where id = :id").param("id", id).update();
    }

    Map<String, String> fields(ResultSet rs) throws SQLException {
        return json.readValue(rs.getString("fields"), FIELDS);
    }

    private Template map(ResultSet rs, int n) throws SQLException {
        return new Template(rs.getObject("id", UUID.class), rs.getString("name"), provider(rs), fields(rs),
                rs.getString("target"), rs.getBigDecimal("amount"), rs.getObject("source_account_id", UUID.class));
    }

    // Витрина поставщика — одинаковые колонки у шаблонов и автоплатежей
    public static ProviderView provider(ResultSet rs) throws SQLException {
        return new ProviderView(rs.getString("provider_code"), rs.getString("provider_group"), rs.getString("provider_name"),
                rs.getString("provider_domain"), rs.getString("provider_color"), rs.getString("provider_country"));
    }
}
