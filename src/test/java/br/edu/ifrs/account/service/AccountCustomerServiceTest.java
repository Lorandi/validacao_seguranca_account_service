package br.edu.ifrs.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import br.edu.ifrs.account.dto.AccountDTO;
import br.edu.ifrs.account.entity.Account;
import br.edu.ifrs.account.enums.AccountStatusEnum;
import br.edu.ifrs.account.repository.AccountRepository;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountCustomerServiceTest {

    private static final Long USER_ID = 10L;
    private static final String ACCOUNT_ID = "CC_10";
    private static final String TARGET_ID = "CC_20";
    private static final String INVALID_AMOUNT = "O valor deve ser maior que zero.";
    private static final String INSUFFICIENT_BALANCE = "Saldo insuficiente.";
    private static final String BLOCKED_ACCOUNT = "Conta bloqueada: CC_10";
    private static final String NOT_OWNER = "Usuário não é dono da conta CC_10";

    @Mock
    AccountRepository repository;

    @Mock
    AccountAuditService audit;

    @Mock
    JsonWebToken jwt;

    @Mock
    PanacheQuery<Account> query;

    @Mock
    PanacheQuery<Account> targetQuery;

    @InjectMocks
    AccountCustomerService service;

    static Stream<Arguments> invalidAmounts() {
        return Stream.of(
                Arguments.of((Object) null),
                Arguments.of(new BigDecimal("0")),
                Arguments.of(new BigDecimal("-0.01")),
                Arguments.of(new BigDecimal("-50.00")));
    }

    // ---------- getByOwnerId ----------

    @Test
    void getByOwnerIdShouldReturnTheDtoOfTheAccountOwnedByTheTokenUser() {
        stubUserId(USER_ID);
        stubFind(query, "ownerId", USER_ID,
                Optional.of(account(ACCOUNT_ID, "100.00", AccountStatusEnum.ACTIVE, USER_ID)));

        AccountDTO result = service.getByOwnerId();

        assertEquals(new AccountDTO(null, ACCOUNT_ID, new BigDecimal("100.00"), "ACTIVE", USER_ID), result);
    }

    @Test
    void getByOwnerIdShouldThrowIllegalArgumentExceptionWhenUserHasNoAccount() {
        stubUserId(USER_ID);
        stubFind(query, "ownerId", USER_ID, Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.getByOwnerId());

        assertEquals("Nenhuma conta encontrada para o usuário 10", exception.getMessage());
    }

    @Test
    void getByOwnerIdShouldThrowIllegalStateExceptionWhenTokenHasNoUserIdClaim() {
        stubUserId(null);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.getByOwnerId());

        assertEquals("Token não contém o claim 'userId'.", exception.getMessage());
        verifyNoInteractions(repository);
    }

    // ---------- create ----------

    @Test
    void createShouldPersistABlockedZeroBalanceAccountAndAuditSuccess() {
        stubUserId(USER_ID);
        when(jwt.getGroups()).thenReturn(Set.of("CUSTOMER"));
        stubFind(query, "ownerId", USER_ID, Optional.empty());

        service.create();

        Account persisted = capturePersisted();
        assertEquals("CC_10", persisted.getAccountId());
        assertEquals(BigDecimal.ZERO, persisted.getBalance());
        assertEquals(AccountStatusEnum.BLOCKED, persisted.getStatus());
        assertEquals(USER_ID, persisted.getOwnerId());
        verify(audit).record("CREATE_ACCOUNT", USER_ID, null, "CC_10", null, "SUCCESS", null);
        verifyNoMoreInteractions(audit);
    }

    @Test
    void createShouldRejectUserWithoutCustomerRole() {
        stubUserId(USER_ID);
        when(jwt.getGroups()).thenReturn(Set.of("ADMIN"));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.create());

        assertEquals("Usuário não possui permissão CUSTOMER.", exception.getMessage());
        verifyCreateFailure("Usuário não possui permissão CUSTOMER.");
    }

    @Test
    void createShouldRejectUserWhoAlreadyHasAnAccount() {
        stubUserId(USER_ID);
        when(jwt.getGroups()).thenReturn(Set.of("CUSTOMER"));
        stubFind(query, "ownerId", USER_ID,
                Optional.of(account(ACCOUNT_ID, "0", AccountStatusEnum.BLOCKED, USER_ID)));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.create());

        assertEquals("Usuário id 10 já possui uma conta.", exception.getMessage());
        verifyCreateFailure("Usuário id 10 já possui uma conta.");
    }

    @Test
    void createShouldRejectTokenWithoutUserIdClaim() {
        stubUserId(null);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.create());

        assertEquals("Token não contém o claim 'userId'.", exception.getMessage());
        verifyCreateFailure("Token não contém o claim 'userId'.");
    }

    @Test
    void createShouldRejectTokenWithEmptyUserIdClaim() {
        stubUserId("");
        when(jwt.getGroups()).thenReturn(Set.of("CUSTOMER"));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.create());

        assertEquals("Token não contém 'userId'.", exception.getMessage());
        verifyCreateFailure("Token não contém 'userId'.");
    }

    // ---------- deposit ----------

    @Test
    void depositShouldIncreaseTheBalancePersistTheSameAccountAndAuditSuccess() {
        Account account = account(ACCOUNT_ID, "100.00", AccountStatusEnum.ACTIVE, USER_ID);
        stubFind(query, "accountId", ACCOUNT_ID, Optional.of(account));
        BigDecimal amount = new BigDecimal("50.25");

        service.deposit(ACCOUNT_ID, amount);

        assertEquals(new BigDecimal("150.25"), account.getBalance());
        assertEquals(account, capturePersisted());
        verify(audit).record("DEPOSIT", USER_ID, null, ACCOUNT_ID, amount, "SUCCESS", null);
        verifyNoMoreInteractions(audit);
    }

    @ParameterizedTest
    @MethodSource("invalidAmounts")
    void depositShouldRejectNullZeroOrNegativeAmountsKeepingTheBalance(BigDecimal amount) {
        Account account = account(ACCOUNT_ID, "100.00", AccountStatusEnum.ACTIVE, USER_ID);
        stubFind(query, "accountId", ACCOUNT_ID, Optional.of(account));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.deposit(ACCOUNT_ID, amount));

        assertEquals(INVALID_AMOUNT, exception.getMessage());
        assertEquals(new BigDecimal("100.00"), account.getBalance());
        verifyNothingPersisted();
        verifyFailure("DEPOSIT", null, null, ACCOUNT_ID, amount, INVALID_AMOUNT);
    }

    @Test
    void depositShouldRejectBlockedAccountKeepingTheBalance() {
        Account account = account(ACCOUNT_ID, "100.00", AccountStatusEnum.BLOCKED, USER_ID);
        stubFind(query, "accountId", ACCOUNT_ID, Optional.of(account));
        BigDecimal amount = new BigDecimal("10.00");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.deposit(ACCOUNT_ID, amount));

        assertEquals(BLOCKED_ACCOUNT, exception.getMessage());
        assertEquals(new BigDecimal("100.00"), account.getBalance());
        verifyNothingPersisted();
        verifyFailure("DEPOSIT", null, null, ACCOUNT_ID, amount, BLOCKED_ACCOUNT);
    }

    @Test
    void depositShouldRejectMissingAccount() {
        stubFind(query, "accountId", "CC_99", Optional.empty());
        BigDecimal amount = new BigDecimal("10.00");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.deposit("CC_99", amount));

        assertEquals("Conta não encontrada: CC_99", exception.getMessage());
        verifyNothingPersisted();
        verifyFailure("DEPOSIT", null, null, "CC_99", amount, "Conta não encontrada: CC_99");
    }

    // ---------- withdraw ----------

    @Test
    void withdrawShouldDecreaseTheBalancePersistTheAccountAndAuditSuccess() {
        stubUserId(USER_ID);
        Account account = account(ACCOUNT_ID, "100.00", AccountStatusEnum.ACTIVE, USER_ID);
        stubFind(query, "accountId", ACCOUNT_ID, Optional.of(account));
        BigDecimal amount = new BigDecimal("30.50");

        service.withdraw(ACCOUNT_ID, amount);

        assertEquals(new BigDecimal("69.50"), account.getBalance());
        assertEquals(account, capturePersisted());
        verify(audit).record("WITHDRAW", USER_ID, null, ACCOUNT_ID, amount, "SUCCESS", null);
        verifyNoMoreInteractions(audit);
    }

    @Test
    void withdrawShouldAllowWithdrawingExactlyTheBalanceLeavingZero() {
        stubUserId(USER_ID);
        Account account = account(ACCOUNT_ID, "100.00", AccountStatusEnum.ACTIVE, USER_ID);
        stubFind(query, "accountId", ACCOUNT_ID, Optional.of(account));
        BigDecimal amount = new BigDecimal("100.00");

        service.withdraw(ACCOUNT_ID, amount);

        assertEquals(new BigDecimal("0.00"), account.getBalance());
        assertEquals(account, capturePersisted());
        verify(audit).record("WITHDRAW", USER_ID, null, ACCOUNT_ID, amount, "SUCCESS", null);
    }

    @Test
    void withdrawShouldRejectUserWhoDoesNotOwnTheAccount() {
        stubUserId(99L);
        Account account = account(ACCOUNT_ID, "100.00", AccountStatusEnum.ACTIVE, USER_ID);
        stubFind(query, "accountId", ACCOUNT_ID, Optional.of(account));
        BigDecimal amount = new BigDecimal("10.00");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.withdraw(ACCOUNT_ID, amount));

        assertEquals(NOT_OWNER, exception.getMessage());
        assertEquals(new BigDecimal("100.00"), account.getBalance());
        verifyNothingPersisted();
        verifyFailure("WITHDRAW", 99L, null, ACCOUNT_ID, amount, NOT_OWNER);
    }

    @Test
    void withdrawShouldRejectBlockedAccount() {
        stubUserId(USER_ID);
        Account account = account(ACCOUNT_ID, "100.00", AccountStatusEnum.BLOCKED, USER_ID);
        stubFind(query, "accountId", ACCOUNT_ID, Optional.of(account));
        BigDecimal amount = new BigDecimal("10.00");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.withdraw(ACCOUNT_ID, amount));

        assertEquals(BLOCKED_ACCOUNT, exception.getMessage());
        assertEquals(new BigDecimal("100.00"), account.getBalance());
        verifyNothingPersisted();
        verifyFailure("WITHDRAW", USER_ID, null, ACCOUNT_ID, amount, BLOCKED_ACCOUNT);
    }

    @Test
    void withdrawShouldRejectAmountGreaterThanTheBalance() {
        stubUserId(USER_ID);
        Account account = account(ACCOUNT_ID, "100.00", AccountStatusEnum.ACTIVE, USER_ID);
        stubFind(query, "accountId", ACCOUNT_ID, Optional.of(account));
        BigDecimal amount = new BigDecimal("100.01");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.withdraw(ACCOUNT_ID, amount));

        assertEquals(INSUFFICIENT_BALANCE, exception.getMessage());
        assertEquals(new BigDecimal("100.00"), account.getBalance());
        verifyNothingPersisted();
        verifyFailure("WITHDRAW", USER_ID, null, ACCOUNT_ID, amount, INSUFFICIENT_BALANCE);
    }

    @ParameterizedTest
    @MethodSource("invalidAmounts")
    void withdrawShouldRejectNullZeroOrNegativeAmountsWithoutQueryingTheAccount(BigDecimal amount) {
        stubUserId(USER_ID);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.withdraw(ACCOUNT_ID, amount));

        assertEquals(INVALID_AMOUNT, exception.getMessage());
        verifyNoInteractions(repository);
        verifyFailure("WITHDRAW", USER_ID, null, ACCOUNT_ID, amount, INVALID_AMOUNT);
    }

    @Test
    void withdrawShouldRejectMissingAccount() {
        stubUserId(USER_ID);
        stubFind(query, "accountId", "CC_99", Optional.empty());
        BigDecimal amount = new BigDecimal("10.00");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.withdraw("CC_99", amount));

        assertEquals("Conta não encontrada: CC_99", exception.getMessage());
        verifyNothingPersisted();
        verifyFailure("WITHDRAW", USER_ID, null, "CC_99", amount, "Conta não encontrada: CC_99");
    }

    // ---------- transfer ----------

    @Test
    void transferShouldDebitSourceCreditTargetPersistBothAndAuditSuccess() {
        stubUserId(USER_ID);
        Account source = account(ACCOUNT_ID, "100.00", AccountStatusEnum.ACTIVE, USER_ID);
        Account target = account(TARGET_ID, "20.00", AccountStatusEnum.ACTIVE, 20L);
        stubFind(query, "accountId", ACCOUNT_ID, Optional.of(source));
        stubFind(targetQuery, "accountId", TARGET_ID, Optional.of(target));
        BigDecimal amount = new BigDecimal("40.00");

        service.transfer(ACCOUNT_ID, TARGET_ID, amount);

        assertEquals(new BigDecimal("60.00"), source.getBalance());
        assertEquals(new BigDecimal("60.00"), target.getBalance());
        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(repository, times(2)).persist(captor.capture());
        assertEquals(List.of(source, target), captor.getAllValues());
        verify(audit).record("TRANSFER", USER_ID, ACCOUNT_ID, TARGET_ID, amount, "SUCCESS", null);
        verifyNoMoreInteractions(audit);
    }

    @ParameterizedTest
    @MethodSource("invalidAmounts")
    void transferShouldRejectNullZeroOrNegativeAmountsWithoutQueryingAccounts(BigDecimal amount) {
        stubUserId(USER_ID);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.transfer(ACCOUNT_ID, TARGET_ID, amount));

        assertEquals(INVALID_AMOUNT, exception.getMessage());
        verifyNoInteractions(repository);
        verifyFailure("TRANSFER", USER_ID, ACCOUNT_ID, TARGET_ID, amount, INVALID_AMOUNT);
    }

    @Test
    void transferShouldRejectSameSourceAndTargetWithoutQueryingAccounts() {
        stubUserId(USER_ID);
        BigDecimal amount = new BigDecimal("10.00");
        String message = "Conta de origem e destino não podem ser iguais.";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.transfer(ACCOUNT_ID, ACCOUNT_ID, amount));

        assertEquals(message, exception.getMessage());
        verifyNoInteractions(repository);
        verifyFailure("TRANSFER", USER_ID, ACCOUNT_ID, ACCOUNT_ID, amount, message);
    }

    @Test
    void transferShouldRejectUserWhoDoesNotOwnTheSourceWithoutQueryingTarget() {
        stubUserId(99L);
        Account source = account(ACCOUNT_ID, "100.00", AccountStatusEnum.ACTIVE, USER_ID);
        stubFind(query, "accountId", ACCOUNT_ID, Optional.of(source));
        BigDecimal amount = new BigDecimal("10.00");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.transfer(ACCOUNT_ID, TARGET_ID, amount));

        assertEquals(NOT_OWNER, exception.getMessage());
        assertEquals(new BigDecimal("100.00"), source.getBalance());
        verifyTargetNeverQueried();
        verifyNothingPersisted();
        verifyFailure("TRANSFER", 99L, ACCOUNT_ID, TARGET_ID, amount, NOT_OWNER);
    }

    @Test
    void transferShouldRejectBlockedSourceWithoutQueryingTarget() {
        stubUserId(USER_ID);
        Account source = account(ACCOUNT_ID, "100.00", AccountStatusEnum.BLOCKED, USER_ID);
        stubFind(query, "accountId", ACCOUNT_ID, Optional.of(source));
        BigDecimal amount = new BigDecimal("10.00");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.transfer(ACCOUNT_ID, TARGET_ID, amount));

        assertEquals(BLOCKED_ACCOUNT, exception.getMessage());
        assertEquals(new BigDecimal("100.00"), source.getBalance());
        verifyTargetNeverQueried();
        verifyNothingPersisted();
        verifyFailure("TRANSFER", USER_ID, ACCOUNT_ID, TARGET_ID, amount, BLOCKED_ACCOUNT);
    }

    @Test
    void transferShouldRejectInsufficientBalanceWithoutQueryingTarget() {
        stubUserId(USER_ID);
        Account source = account(ACCOUNT_ID, "100.00", AccountStatusEnum.ACTIVE, USER_ID);
        stubFind(query, "accountId", ACCOUNT_ID, Optional.of(source));
        BigDecimal amount = new BigDecimal("100.01");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.transfer(ACCOUNT_ID, TARGET_ID, amount));

        assertEquals(INSUFFICIENT_BALANCE, exception.getMessage());
        assertEquals(new BigDecimal("100.00"), source.getBalance());
        verifyTargetNeverQueried();
        verifyNothingPersisted();
        verifyFailure("TRANSFER", USER_ID, ACCOUNT_ID, TARGET_ID, amount, INSUFFICIENT_BALANCE);
    }

    @Test
    void transferShouldRejectMissingTargetKeepingTheSourceBalance() {
        stubUserId(USER_ID);
        Account source = account(ACCOUNT_ID, "100.00", AccountStatusEnum.ACTIVE, USER_ID);
        stubFind(query, "accountId", ACCOUNT_ID, Optional.of(source));
        stubFind(targetQuery, "accountId", TARGET_ID, Optional.empty());
        BigDecimal amount = new BigDecimal("10.00");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.transfer(ACCOUNT_ID, TARGET_ID, amount));

        assertEquals("Conta não encontrada: CC_20", exception.getMessage());
        assertEquals(new BigDecimal("100.00"), source.getBalance());
        verifyNothingPersisted();
        verifyFailure("TRANSFER", USER_ID, ACCOUNT_ID, TARGET_ID, amount, "Conta não encontrada: CC_20");
    }

    @Test
    void transferShouldRejectBlockedTargetKeepingBothBalances() {
        stubUserId(USER_ID);
        Account source = account(ACCOUNT_ID, "100.00", AccountStatusEnum.ACTIVE, USER_ID);
        Account target = account(TARGET_ID, "20.00", AccountStatusEnum.BLOCKED, 20L);
        stubFind(query, "accountId", ACCOUNT_ID, Optional.of(source));
        stubFind(targetQuery, "accountId", TARGET_ID, Optional.of(target));
        BigDecimal amount = new BigDecimal("10.00");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.transfer(ACCOUNT_ID, TARGET_ID, amount));

        assertEquals("Conta bloqueada: CC_20", exception.getMessage());
        assertEquals(new BigDecimal("100.00"), source.getBalance());
        assertEquals(new BigDecimal("20.00"), target.getBalance());
        verifyNothingPersisted();
        verifyFailure("TRANSFER", USER_ID, ACCOUNT_ID, TARGET_ID, amount, "Conta bloqueada: CC_20");
    }

    // ---------- claim userId ausente ou inválido ----------

    @Test
    void withdrawShouldAuditFailureWithNullOwnerWhenTokenHasNoUserIdClaim() {
        stubUserId(null);
        BigDecimal amount = new BigDecimal("10.00");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.withdraw(ACCOUNT_ID, amount));

        assertEquals("Token não contém o claim 'userId'.", exception.getMessage());
        verifyNoInteractions(repository);
        verifyFailure("WITHDRAW", null, null, ACCOUNT_ID, amount, "Token não contém o claim 'userId'.");
    }

    @Test
    void transferShouldAuditFailureWithNullOwnerWhenTokenHasNoUserIdClaim() {
        stubUserId(null);
        BigDecimal amount = new BigDecimal("10.00");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.transfer(ACCOUNT_ID, TARGET_ID, amount));

        assertEquals("Token não contém o claim 'userId'.", exception.getMessage());
        verifyNoInteractions(repository);
        verifyFailure("TRANSFER", null, ACCOUNT_ID, TARGET_ID, amount, "Token não contém o claim 'userId'.");
    }

    @Test
    void getByOwnerIdShouldRejectNonNumericUserIdClaimWithControlledMessage() {
        stubUserId("abc");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.getByOwnerId());

        assertEquals("Token contém um 'userId' inválido.", exception.getMessage());
        verifyNoInteractions(repository);
    }

    @Test
    void withdrawShouldRejectNonNumericUserIdClaimAndAuditFailure() {
        stubUserId("abc");
        BigDecimal amount = new BigDecimal("10.00");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.withdraw(ACCOUNT_ID, amount));

        assertEquals("Token contém um 'userId' inválido.", exception.getMessage());
        verifyNoInteractions(repository);
        verifyFailure("WITHDRAW", null, null, ACCOUNT_ID, amount, "Token contém um 'userId' inválido.");
    }

    // ---------- helpers ----------

    private void stubUserId(Object userId) {
        when(jwt.<Object>getClaim("userId")).thenReturn(userId);
    }

    private void stubFind(PanacheQuery<Account> target, String field, Object value, Optional<Account> result) {
        when(repository.find(field, value)).thenReturn(target);
        when(target.firstResultOptional()).thenReturn(result);
    }

    private Account capturePersisted() {
        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(repository).persist(captor.capture());
        return captor.getValue();
    }

    private void verifyNothingPersisted() {
        verify(repository, never()).persist(any(Account.class));
    }

    private void verifyTargetNeverQueried() {
        verify(repository, never()).find("accountId", TARGET_ID);
    }

    private void verifyCreateFailure(String message) {
        verifyNothingPersisted();
        verify(audit).record("CREATE_ACCOUNT", null, null, null, null, "FAILURE", message);
        verifyNoMoreInteractions(audit);
    }

    private void verifyFailure(String action, Long ownerId, String source, String target,
                               BigDecimal amount, String message) {
        verify(audit).record(action, ownerId, source, target, amount, "FAILURE", message);
        verifyNoMoreInteractions(audit);
    }

    private static Account account(String accountId, String balance, AccountStatusEnum status, Long ownerId) {
        return new Account(accountId, new BigDecimal(balance), status, ownerId);
    }
}
