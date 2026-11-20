package br.edu.ifrs.account.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.edu.ifrs.account.dto.AccountAuditDTO;
import br.edu.ifrs.account.entity.AccountAudit;
import br.edu.ifrs.account.repository.AccountAuditRepository;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountAuditServiceTest {

    private static final LocalDateTime TIMESTAMP = LocalDateTime.of(2026, 11, 20, 10, 30, 0);

    @Mock
    AccountAuditRepository repository;

    @Mock
    PanacheQuery<AccountAudit> query;

    @InjectMocks
    AccountAuditService service;

    @Test
    void recordShouldPersistAnAuditWithTheInformedData() {
        LocalDateTime before = LocalDateTime.now();

        service.record("TRANSFER", 10L, "ACC1002", "ACC1004", new BigDecimal("25.50"), "FAILURE",
                "Saldo insuficiente.");

        LocalDateTime after = LocalDateTime.now();
        AccountAudit persisted = capturePersistedAudit();
        assertEquals("TRANSFER", persisted.getAction());
        assertEquals(10L, persisted.getOwnerId());
        assertEquals("ACC1002", persisted.getSourceAccountId());
        assertEquals("ACC1004", persisted.getTargetAccountId());
        assertEquals(new BigDecimal("25.50"), persisted.getAmount());
        assertEquals("FAILURE", persisted.getResult());
        assertEquals("Saldo insuficiente.", persisted.getErrorMessage());
        assertFalse(persisted.getTimestamp().isBefore(before));
        assertFalse(persisted.getTimestamp().isAfter(after));
    }

    @Test
    void recordShouldGenerateAValidUuidAsOperationId() {
        service.record("DEPOSIT", 10L, null, "ACC1002", new BigDecimal("10.00"), "SUCCESS", null);

        String operationId = capturePersistedAudit().getOperationId();
        assertEquals(operationId, UUID.fromString(operationId).toString());
    }

    @Test
    void recordShouldKeepOptionalFieldsNullWhenTheyAreNotInformed() {
        service.record("TOGGLE_STATUS", null, null, "ACC1002", null, "SUCCESS", null);

        AccountAudit persisted = capturePersistedAudit();
        assertNull(persisted.getOwnerId());
        assertNull(persisted.getSourceAccountId());
        assertNull(persisted.getAmount());
        assertNull(persisted.getErrorMessage());
        assertEquals("ACC1002", persisted.getTargetAccountId());
    }

    @Test
    void recordShouldNotPropagateTheExceptionWhenPersistFails() {
        doThrow(new IllegalStateException("database unavailable")).when(repository).persist(any(AccountAudit.class));

        assertDoesNotThrow(() -> service.record("WITHDRAW", 10L, "ACC1002", null,
                new BigDecimal("5.00"), "SUCCESS", null));

        verify(repository).persist(any(AccountAudit.class));
    }

    @Test
    void listAllShouldConvertEntitiesToDtos() {
        AccountAudit first = audit("op-1", "DEPOSIT", 10L, null, "ACC1002", new BigDecimal("100.00"), "SUCCESS",
                null);
        AccountAudit second = audit("op-2", "TRANSFER", 11L, "ACC1004", "ACC1002", new BigDecimal("30.00"),
                "FAILURE", "Conta bloqueada: ACC1004");
        when(repository.listAll()).thenReturn(List.of(first, second));

        List<AccountAuditDTO> result = service.listAll();

        assertEquals(2, result.size());
        AccountAuditDTO firstDto = result.get(0);
        assertEquals("op-1", firstDto.operationId());
        assertEquals("DEPOSIT", firstDto.action());
        assertEquals(10L, firstDto.ownerId());
        assertNull(firstDto.sourceAccountId());
        assertEquals("ACC1002", firstDto.targetAccountId());
        assertEquals(new BigDecimal("100.00"), firstDto.amount());
        assertEquals("SUCCESS", firstDto.result());
        assertEquals(TIMESTAMP, firstDto.timestamp());
        assertNull(firstDto.errorMessage());
        AccountAuditDTO secondDto = result.get(1);
        assertEquals("op-2", secondDto.operationId());
        assertEquals("ACC1004", secondDto.sourceAccountId());
        assertEquals("FAILURE", secondDto.result());
        assertEquals("Conta bloqueada: ACC1004", secondDto.errorMessage());
    }

    @Test
    void recentShouldOrderByTimestampDescendingAndLimitThePage() {
        AccountAudit audit = audit("op-3", "WITHDRAW", 10L, "ACC1002", null, new BigDecimal("20.00"), "SUCCESS",
                null);
        when(repository.find("order by timestamp desc")).thenReturn(query);
        when(query.page(0, 5)).thenReturn(query);
        when(query.list()).thenReturn(List.of(audit));

        List<AccountAuditDTO> result = service.recent(5);

        verify(query).page(0, 5);
        assertEquals(1, result.size());
        assertEquals("op-3", result.get(0).operationId());
        assertEquals("WITHDRAW", result.get(0).action());
        assertEquals(new BigDecimal("20.00"), result.get(0).amount());
    }

    @Test
    void byOwnerShouldQueryByOwnerIdAndConvertTheResult() {
        AccountAudit audit = audit("op-4", "DEPOSIT", 10L, null, "ACC1002", new BigDecimal("50.00"), "SUCCESS", null);
        when(repository.list("ownerId", 10L)).thenReturn(List.of(audit));

        List<AccountAuditDTO> result = service.byOwner(10L);

        assertEquals(1, result.size());
        assertEquals("op-4", result.get(0).operationId());
        assertEquals(10L, result.get(0).ownerId());
        assertEquals(TIMESTAMP, result.get(0).timestamp());
    }

    @Test
    void byAccountIdShouldQueryBySourceOrTargetAccountAndConvertTheResult() {
        AccountAudit asSource = audit("op-5", "TRANSFER", 10L, "ACC1002", "ACC1004", new BigDecimal("15.00"),
                "SUCCESS", null);
        AccountAudit asTarget = audit("op-6", "TRANSFER", 11L, "ACC1004", "ACC1002", new BigDecimal("8.00"),
                "SUCCESS", null);
        when(repository.list("sourceAccountId = ?1 or targetAccountId = ?1", "ACC1002"))
                .thenReturn(List.of(asSource, asTarget));

        List<AccountAuditDTO> result = service.byAccountId("ACC1002");

        assertEquals(2, result.size());
        assertEquals("op-5", result.get(0).operationId());
        assertEquals("ACC1002", result.get(0).sourceAccountId());
        assertEquals("op-6", result.get(1).operationId());
        assertEquals("ACC1002", result.get(1).targetAccountId());
    }

    @Test
    void queriesShouldReturnAnEmptyListWhenThereAreNoAudits() {
        when(repository.list("ownerId", 99L)).thenReturn(List.of());

        List<AccountAuditDTO> result = service.byOwner(99L);

        assertTrue(result.isEmpty());
    }

    private AccountAudit capturePersistedAudit() {
        ArgumentCaptor<AccountAudit> captor = ArgumentCaptor.forClass(AccountAudit.class);
        verify(repository).persist(captor.capture());
        return captor.getValue();
    }

    private static AccountAudit audit(String operationId, String action, Long ownerId, String source, String target,
                                      BigDecimal amount, String result, String errorMessage) {
        return new AccountAudit(operationId, action, ownerId, source, target, amount, result, TIMESTAMP,
                errorMessage);
    }
}
