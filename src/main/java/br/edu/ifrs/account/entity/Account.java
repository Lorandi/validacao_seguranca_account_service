package br.edu.ifrs.account.entity;

import br.edu.ifrs.account.enums.AccountStatusEnum;
import jakarta.persistence.*;

import java.math.BigDecimal;

import static jakarta.persistence.EnumType.STRING;

@Entity
@Table(name = "account")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String accountId;

    @Column(nullable = false)
    private BigDecimal balance = BigDecimal.ZERO;

    @Enumerated(STRING)
    @Column(nullable = false)
    private AccountStatusEnum status;

    @Column(nullable = false, unique = true)
    private Long ownerId;

    public Account() {
    }

    public Account(String accountId, BigDecimal balance, AccountStatusEnum status, Long ownerId) {
        this.accountId = accountId;
        this.balance = balance;
        this.status = status;
        this.ownerId = ownerId;
    }

    public Long getId() {
        return id;
    }

    public String getAccountId() {
        return accountId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public AccountStatusEnum getStatus() {
        return status;
    }

    public void setStatus(AccountStatusEnum status) {
        this.status = status;
    }

    public Long getOwnerId() {
        return ownerId;
    }
}
