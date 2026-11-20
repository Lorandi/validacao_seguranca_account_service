package br.edu.ifrs.account.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "admin_audit")
public class AccountAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private String operationId;

    private String action;

    private Long ownerId;

    private String sourceAccountId;

    private String targetAccountId;

    @Column(precision = 18, scale = 2)
    private BigDecimal amount;

    private String result;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(length = 200)
    private String errorMessage;

    public AccountAudit() {
    }

    public AccountAudit(String operationId, String action, Long ownerId, String sourceAccountId,
                        String targetAccountId, BigDecimal amount, String result,
                        LocalDateTime timestamp, String errorMessage) {
        this.operationId = operationId;
        this.action = action;
        this.ownerId = ownerId;
        this.sourceAccountId = sourceAccountId;
        this.targetAccountId = targetAccountId;
        this.amount = amount;
        this.result = result;
        this.timestamp = timestamp;
        this.errorMessage = errorMessage;
    }

    public Long getId() {
        return id;
    }

    public String getOperationId() {
        return operationId;
    }

    public String getAction() {
        return action;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public String getSourceAccountId() {
        return sourceAccountId;
    }

    public String getTargetAccountId() {
        return targetAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getResult() {
        return result;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
