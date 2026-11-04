package com.paymentsapp.autopayments.template;

import com.paymentsapp.autopayments.template.Template.ProviderView;
import com.paymentsapp.autopayments.web.ApiException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class TemplateService {

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final Target target;

    public TemplateService(JdbcClient jdbc, JsonMapper json, Target target) {
        this.jdbc = jdbc;
        this.json = json;
        this.target = target;
    }

    public List<Template> list(UUID userId) {
        return jdbc.sql("select * from templates where user_id = :user order by created_at")
                .param("user", userId).query(this::map).list();
    }

    public Template get(UUID userId, UUID id) {
        return jdbc.sql("select * from templates where id = :id and user_id = :user").param("id", id).param("user", userId)
                .query(this::map).optional().orElseThrow(() -> ApiException.notFound("Шаблон не найден"));
    }

    public Template create(UUID userId, Request req) {
        Target.Checked t = target.check(userId, req.providerCode(), req.fields(), req.amount(), List.of(req.sourceAccountId()));
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        insert into templates (id, user_id, name, provider_code, provider_group, provider_name, provider_domain,
                            provider_color, provider_country, fields, target, amount, source_account_id)
                        values (:id, :user, :name, :code, :group, :pname, :domain, :color, :country, cast(:fields as jsonb),
                            :target, :amount, :source)
                        """)
                .param("id", id).param("user", userId).param("name", name(req.name()))
                .param("code", t.provider().code()).param("group", t.provider().groupCode()).param("pname", t.provider().name())
                .param("domain", t.provider().domain()).param("color", t.provider().color()).param("country", t.provider().countryCode())
                .param("fields", json.writeValueAsString(t.fields())).param("target", t.target())
                .param("amount", req.amount()).param("source", req.sourceAccountId())
                .update();
        return get(userId, id);
    }

    public Template update(UUID userId, UUID id, Request req) {
        Template old = get(userId, id);
        Target.Checked t = target.check(userId, old.provider().code(), req.fields(), req.amount(), List.of(req.sourceAccountId()));
        jdbc.sql("""
                        update templates set name = :name, fields = cast(:fields as jsonb), target = :target, amount = :amount,
                            source_account_id = :source, updated_at = now()
                        where id = :id
                        """)
                .param("id", id).param("name", name(req.name())).param("fields", json.writeValueAsString(t.fields()))
                .param("target", t.target()).param("amount", req.amount()).param("source", req.sourceAccountId())
                .update();
        return get(userId, id);
    }

    public void delete(UUID userId, UUID id) {
        get(userId, id);
        jdbc.sql("delete from templates where id = :id").param("id", id).update();
    }

    static String name(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || n.length() > 40) {
            throw ApiException.badRequest("Название шаблона — от 1 до 40 символов");
        }
        return n;
    }

    private Template map(ResultSet rs, int n) throws SQLException {
        return new Template(rs.getObject("id", UUID.class), rs.getString("name"), provider(rs),
                json.readValue(rs.getString("fields"), new TypeReference<Map<String, String>>() { }), rs.getString("target"),
                rs.getBigDecimal("amount"), rs.getObject("source_account_id", UUID.class));
    }

    static ProviderView provider(ResultSet rs) throws SQLException {
        return new ProviderView(rs.getString("provider_code"), rs.getString("provider_group"), rs.getString("provider_name"),
                rs.getString("provider_domain"), rs.getString("provider_color"), rs.getString("provider_country"));
    }

    public record Request(String name, String providerCode, Map<String, String> fields, BigDecimal amount, UUID sourceAccountId) {
    }
}
