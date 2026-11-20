package br.edu.ifrs.account.repository;

import br.edu.ifrs.account.entity.AccountAudit;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AccountAuditRepository implements PanacheRepository<AccountAudit> {
}
