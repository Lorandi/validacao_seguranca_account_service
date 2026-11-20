package br.edu.ifrs.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import br.edu.ifrs.account.entity.Account;
import br.edu.ifrs.account.enums.AccountStatusEnum;
import br.edu.ifrs.account.repository.AccountRepository;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountAdminServiceTest {

    private static final String ACCOUNT_ID = "ACC1002";
    private static final Long OWNER_ID = 10L;

    @Mock
    AccountRepository repository;

    @Mock
    AccountAuditService audit;

    @Mock
    PanacheQuery<Account> query;

    @InjectMocks
    AccountAdminService service;

    @Test
    void listAllShouldReturnTheAccountsProvidedByTheRepository() {
        List<Account> accounts = List.of(
                account(ACCOUNT_ID, AccountStatusEnum.ACTIVE, OWNER_ID),
                account("ACC1005", AccountStatusEnum.BLOCKED, 11L));
        when(repository.listAll()).thenReturn(accounts);

        List<Account> result = service.listAll();

        assertSame(accounts, result);
    }

    @Test
    void getByAccountIdShouldReturnTheExistingAccount() {
        Account account = account(ACCOUNT_ID, AccountStatusEnum.ACTIVE, OWNER_ID);
        stubFind("accountId", ACCOUNT_ID, Optional.of(account));

        Account result = service.getByAccountId(ACCOUNT_ID);

        assertSame(account, result);
    }

    @Test
    void getByAccountIdShouldThrowIllegalArgumentExceptionWhenAccountDoesNotExist() {
        stubFind("accountId", "ACC9999", Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.getByAccountId("ACC9999"));

        assertEquals("Conta não encontrada: ACC9999", exception.getMessage());
    }

    @Test
    void getByOwnerIdShouldReturnTheExistingAccount() {
        Account account = account(ACCOUNT_ID, AccountStatusEnum.ACTIVE, OWNER_ID);
        stubFind("ownerId", OWNER_ID, Optional.of(account));

        Account result = service.getByOwnerId(OWNER_ID);

        assertSame(account, result);
    }

    @Test
    void getByOwnerIdShouldThrowIllegalArgumentExceptionWhenAccountDoesNotExist() {
        stubFind("ownerId", 99L, Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.getByOwnerId(99L));

        assertEquals("Nenhuma conta encontrada para o usuário 99", exception.getMessage());
    }

    @Test
    void toggleStatusShouldBlockAnActiveAccountAndRecordSuccessAudit() {
        Account account = account(ACCOUNT_ID, AccountStatusEnum.ACTIVE, OWNER_ID);
        stubFind("accountId", ACCOUNT_ID, Optional.of(account));

        Account result = service.toggleStatus(ACCOUNT_ID);

        assertSame(account, result);
        assertEquals(AccountStatusEnum.BLOCKED, result.getStatus());
        verifySuccessfulToggle(account);
    }

    @Test
    void toggleStatusShouldUnblockABlockedAccountAndRecordSuccessAudit() {
        Account account = account(ACCOUNT_ID, AccountStatusEnum.BLOCKED, OWNER_ID);
        stubFind("accountId", ACCOUNT_ID, Optional.of(account));

        Account result = service.toggleStatus(ACCOUNT_ID);

        assertSame(account, result);
        assertEquals(AccountStatusEnum.ACTIVE, result.getStatus());
        verifySuccessfulToggle(account);
    }

    @Test
    void toggleStatusShouldRethrowAndRecordFailureAuditWhenAccountDoesNotExist() {
        stubFind("accountId", "ACC9999", Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.toggleStatus("ACC9999"));

        assertEquals("Conta não encontrada: ACC9999", exception.getMessage());
        verify(repository, never()).persist(any(Account.class));
        verify(audit).record("TOGGLE_STATUS", null, null, "ACC9999", null, "FAILURE",
                "Conta não encontrada: ACC9999");
        verifyNoMoreInteractions(audit);
    }

    private void verifySuccessfulToggle(Account account) {
        verify(repository).persist(account);
        verify(audit).record("TOGGLE_STATUS", OWNER_ID, null, ACCOUNT_ID, null, "SUCCESS", null);
        verifyNoMoreInteractions(audit);
    }

    private void stubFind(String field, Object value, Optional<Account> result) {
        when(repository.find(field, value)).thenReturn(query);
        when(query.firstResultOptional()).thenReturn(result);
    }

    private static Account account(String accountId, AccountStatusEnum status, Long ownerId) {
        return new Account(accountId, new BigDecimal("100.00"), status, ownerId);
    }
}
