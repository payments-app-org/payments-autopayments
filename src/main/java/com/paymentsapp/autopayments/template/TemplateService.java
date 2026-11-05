package com.paymentsapp.autopayments.template;

import static com.paymentsapp.autopayments.error.AutopaymentsError.TEMPLATE_NAME_LENGTH;

import com.paymentsapp.autopayments.config.AutopayProperties;
import com.paymentsapp.platform.error.DomainException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TemplateService {

    private final TemplateRepository repo;
    private final Target target;
    private final AutopayProperties props;

    public TemplateService(TemplateRepository repo, Target target, AutopayProperties props) {
        this.repo = repo;
        this.target = target;
        this.props = props;
    }

    public List<Template> list(UUID userId) {
        return repo.list(userId);
    }

    public Template get(UUID userId, UUID id) {
        return repo.get(userId, id);
    }

    public Template create(UUID userId, Request req) {
        Target.Checked t = target.check(userId, req.providerCode(), req.fields(), req.amount(),
                Collections.singletonList(req.sourceAccountId()));
        UUID id = UUID.randomUUID();
        repo.insert(id, userId, name(req.name()), t, req.amount(), req.sourceAccountId());
        return repo.get(userId, id);
    }

    public Template update(UUID userId, UUID id, Request req) {
        Template old = repo.get(userId, id);
        Target.Checked t = target.check(userId, old.provider().code(), req.fields(), req.amount(),
                Collections.singletonList(req.sourceAccountId()));
        repo.update(id, name(req.name()), t, req.amount(), req.sourceAccountId());
        return repo.get(userId, id);
    }

    public void delete(UUID userId, UUID id) {
        repo.get(userId, id);
        repo.delete(id);
    }

    private String name(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || n.length() > props.templateNameMax()) {
            throw new DomainException(TEMPLATE_NAME_LENGTH, props.templateNameMax());
        }
        return n;
    }

    public record Request(String name, String providerCode, Map<String, String> fields, BigDecimal amount, UUID sourceAccountId) {
    }
}
