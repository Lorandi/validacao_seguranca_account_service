package br.edu.ifrs.account.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import br.edu.ifrs.account.auth.UserRepository;
import br.edu.ifrs.account.entity.Account;
import br.edu.ifrs.account.enums.AccountStatusEnum;
import br.edu.ifrs.account.entity.AccountAudit;
import br.edu.ifrs.account.repository.AccountAuditRepository;
import br.edu.ifrs.account.repository.AccountRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.config.JsonPathConfig;
import io.restassured.path.json.config.JsonPathConfig.NumberReturnType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@QuarkusTest
class AccountCustomerResourceTest {

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String OWNER_PATH = "/api/v1/accounts/owner/";
    private static final String DEPOSIT_PATH = "/api/v1/accounts/{accountId}/deposit";

    private static final String CUSTOMER_ACCOUNT = "ACC1002";
    private static final String ACTIVE_THIRD_PARTY_ACCOUNT = "ACC1004";
    private static final String BLOCKED_ACCOUNT = "ACC1005";
    private static final String MISSING_ACCOUNT = "ACC9999";

    private static final String WITHDRAW_PATH = "/api/v1/accounts/{accountId}/withdraw";

    private static final String DEPOSIT_ACTION = "DEPOSIT";
    private static final String WITHDRAW_ACTION = "WITHDRAW";
    private static final String TRANSFER_ACTION = "TRANSFER";
    private static final String CREATE_ACCOUNT_ACTION = "CREATE_ACCOUNT";
    private static final String REGISTER_PATH = "/api/v1/auth/register";
    private static final String ACCOUNTS_PATH = "/api/v1/accounts";
    private static final String EXCLUSIVE_USERNAME = "component_account_user";
    private static final String EXCLUSIVE_PASSWORD = "conta-segura-123";
    private static final String TRANSFER_PATH = "/api/v1/accounts/{accountId}/transfer";
    private static final List<String> TOUCHED_ACTIONS = List.of(DEPOSIT_ACTION, WITHDRAW_ACTION, TRANSFER_ACTION);
    private static final String INVALID_AMOUNT_MESSAGE = "O valor deve ser maior que zero.";

    private static final BigDecimal CUSTOMER_ACCOUNT_BALANCE = new BigDecimal("2750.50");
    private static final BigDecimal THIRD_PARTY_ACCOUNT_BALANCE = new BigDecimal("500.00");
    private static final BigDecimal BLOCKED_ACCOUNT_BALANCE = new BigDecimal("500.00");

    /** Saldos de demonstração das contas que este teste nunca deve alterar nem restaurar. */
    private static final Map<String, BigDecimal> UNTOUCHED_BALANCES = Map.of(
            "ACC1001", new BigDecimal("1500.00"),
            "ACC1003", new BigDecimal("0.00"),
            "ACC2001", new BigDecimal("12500.75"));

    private static final List<String> TOUCHED_ACCOUNTS = List.of(
            CUSTOMER_ACCOUNT, ACTIVE_THIRD_PARTY_ACCOUNT, BLOCKED_ACCOUNT, MISSING_ACCOUNT);

    @Inject
    AccountRepository accountRepository;

    @Inject
    AccountAuditRepository auditRepository;

    @Inject
    UserRepository userRepository;

    private long accountCountBeforeTest;
    private Set<Long> createAuditIdsBeforeTest;

    @BeforeEach
    void prepareData() {
        restoreData();
        accountCountBeforeTest = QuarkusTransaction.requiringNew().call(() -> accountRepository.count());
        createAuditIdsBeforeTest = QuarkusTransaction.requiringNew().call(() ->
                auditRepository.list("action", CREATE_ACCOUNT_ACTION).stream()
                        .map(AccountAudit::getId)
                        .collect(Collectors.toSet()));
    }

    @AfterEach
    void restoreData() {
        QuarkusTransaction.requiringNew().run(() -> {
            if (createAuditIdsBeforeTest != null) {
                // remove somente as auditorias de criação geradas pelo teste corrente
                auditRepository.list("action", CREATE_ACCOUNT_ACTION).stream()
                        .filter(audit -> !createAuditIdsBeforeTest.contains(audit.getId()))
                        .forEach(auditRepository::delete);
            }
            // a conta deve ser removida antes do usuário dono dela
            accountRepository.delete(
                    "ownerId in (select u.id from User u where u.username = ?1)", EXCLUSIVE_USERNAME);
            userRepository.delete("username", EXCLUSIVE_USERNAME);
            auditRepository.delete("action in ?1 and targetAccountId in ?2", TOUCHED_ACTIONS, TOUCHED_ACCOUNTS);
            restoreBalance(CUSTOMER_ACCOUNT, CUSTOMER_ACCOUNT_BALANCE);
            restoreBalance(ACTIVE_THIRD_PARTY_ACCOUNT, THIRD_PARTY_ACCOUNT_BALANCE);
            restoreBalance(BLOCKED_ACCOUNT, BLOCKED_ACCOUNT_BALANCE);
        });
    }

    static Stream<Arguments> invalidAmounts() {
        return Stream.of(
                Arguments.of("amount ausente", null, null),
                Arguments.of("amount igual a zero", "0", new BigDecimal("0")),
                Arguments.of("amount negativo", "-0.01", new BigDecimal("-0.01")));
    }

    // ---------- consulta da conta do proprietário ----------

    @Test
    void getByOwnerShouldReturnTheAuthenticatedCustomerAccountWithoutModifyingIt() {
        Response response = given()
                .auth().oauth2(customerToken())
                .when().get(OWNER_PATH)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("accountId", equalTo(CUSTOMER_ACCOUNT))
                .body("status", equalTo("ACTIVE"))
                .body("ownerId", equalTo(2))
                .extract().response();

        assertMoney(CUSTOMER_ACCOUNT_BALANCE, bigDecimalFrom(response, "balance"));
        assertMoney(CUSTOMER_ACCOUNT_BALANCE, balanceOf(CUSTOMER_ACCOUNT));
        assertEquals(0, depositAudits().size(), "Consulta não deve gerar auditoria");
    }

    // ---------- depósito válido ----------

    @Test
    void depositShouldBeAcceptedFromAnotherCustomerAndCreditOnlyTheTargetAccount() {
        given()
                .auth().oauth2(customerToken())
                .queryParam("amount", "25.50")
                .when().patch(DEPOSIT_PATH, ACTIVE_THIRD_PARTY_ACCOUNT)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("message", equalTo("Depósito realizado com sucesso!"));

        // O token é de "customer" (dono de ACC1002), mas a regra do projeto permite depositar em conta de terceiro.
        assertMoney(new BigDecimal("525.50"), balanceOf(ACTIVE_THIRD_PARTY_ACCOUNT));
        assertMoney(CUSTOMER_ACCOUNT_BALANCE, balanceOf(CUSTOMER_ACCOUNT));
        assertUntouchedAccounts();

        List<AccountAudit> audits = depositAudits();
        assertEquals(1, audits.size());
        AccountAudit audit = audits.get(0);
        assertEquals(DEPOSIT_ACTION, audit.getAction());
        assertEquals("SUCCESS", audit.getResult());
        assertEquals(4L, audit.getOwnerId());
        assertEquals(ACTIVE_THIRD_PARTY_ACCOUNT, audit.getTargetAccountId());
        assertMoney(new BigDecimal("25.50"), audit.getAmount());
        assertNull(audit.getSourceAccountId());
        assertNull(audit.getErrorMessage());
        assertNotNull(audit.getOperationId());
        assertNotNull(audit.getTimestamp());
    }

    // ---------- falhas de depósito ----------

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidAmounts")
    void depositShouldReturn400AndPreserveBalancesWhenAmountIsInvalid(
            String scenario, String amountParam, BigDecimal expectedAuditAmount) {
        RequestSpecification request = given().auth().oauth2(customerToken());
        if (amountParam != null) {
            request.queryParam("amount", amountParam);
        }

        request.when().patch(DEPOSIT_PATH, ACTIVE_THIRD_PARTY_ACCOUNT)
                .then()
                .statusCode(400)
                .body("error", equalTo(INVALID_AMOUNT_MESSAGE));

        assertMoney(THIRD_PARTY_ACCOUNT_BALANCE, balanceOf(ACTIVE_THIRD_PARTY_ACCOUNT));
        assertMoney(CUSTOMER_ACCOUNT_BALANCE, balanceOf(CUSTOMER_ACCOUNT));
        assertMoney(BLOCKED_ACCOUNT_BALANCE, balanceOf(BLOCKED_ACCOUNT));
        assertUntouchedAccounts();
        assertFailureAudit(ACTIVE_THIRD_PARTY_ACCOUNT, expectedAuditAmount, INVALID_AMOUNT_MESSAGE);
    }

    @Test
    void depositShouldReturn409AndPreserveBalanceWhenAccountIsBlocked() {
        given()
                .auth().oauth2(customerToken())
                .queryParam("amount", "10.00")
                .when().patch(DEPOSIT_PATH, BLOCKED_ACCOUNT)
                .then()
                .statusCode(409)
                .body("error", equalTo("Conta bloqueada: ACC1005"));

        assertMoney(BLOCKED_ACCOUNT_BALANCE, balanceOf(BLOCKED_ACCOUNT));
        assertMoney(CUSTOMER_ACCOUNT_BALANCE, balanceOf(CUSTOMER_ACCOUNT));
        assertMoney(THIRD_PARTY_ACCOUNT_BALANCE, balanceOf(ACTIVE_THIRD_PARTY_ACCOUNT));
        assertUntouchedAccounts();
        assertFailureAudit(BLOCKED_ACCOUNT, new BigDecimal("10.00"), "Conta bloqueada: ACC1005");
    }

    @Test
    void depositShouldReturn400AndNotCreateAccountWhenAccountDoesNotExist() {
        given()
                .auth().oauth2(customerToken())
                .queryParam("amount", "10.00")
                .when().patch(DEPOSIT_PATH, MISSING_ACCOUNT)
                .then()
                .statusCode(400)
                .body("error", equalTo("Conta não encontrada: ACC9999"));

        assertEquals(0, accountsNamed(MISSING_ACCOUNT));
        assertEquals(accountCountBeforeTest, QuarkusTransaction.requiringNew().call(() -> accountRepository.count()));
        assertMoney(CUSTOMER_ACCOUNT_BALANCE, balanceOf(CUSTOMER_ACCOUNT));
        assertMoney(THIRD_PARTY_ACCOUNT_BALANCE, balanceOf(ACTIVE_THIRD_PARTY_ACCOUNT));
        assertMoney(BLOCKED_ACCOUNT_BALANCE, balanceOf(BLOCKED_ACCOUNT));
        assertUntouchedAccounts();
        assertFailureAudit(MISSING_ACCOUNT, new BigDecimal("10.00"), "Conta não encontrada: ACC9999");
    }

    // ---------- saque ----------

    @Test
    void withdrawShouldDebitTheOwnAccountAndAuditTheSuccess() {
        assertSuccessfulWithdrawal("50.00", "2700.50");
    }

    @Test
    void withdrawShouldAllowWithdrawingExactlyTheWholeBalance() {
        // valor-limite: saque igual ao saldo deixa a conta zerada
        assertSuccessfulWithdrawal("2750.50", "0.00");
    }

    private void assertSuccessfulWithdrawal(String amount, String expectedBalance) {
        given()
                .auth().oauth2(customerToken())
                .queryParam("amount", amount)
                .when().patch(WITHDRAW_PATH, CUSTOMER_ACCOUNT)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("message", equalTo("Saque realizado com sucesso!"));

        assertBalances(new BigDecimal(expectedBalance), THIRD_PARTY_ACCOUNT_BALANCE, BLOCKED_ACCOUNT_BALANCE);

        List<AccountAudit> audits = auditsFor(WITHDRAW_ACTION);
        assertEquals(1, audits.size());
        AccountAudit audit = audits.get(0);
        assertEquals(WITHDRAW_ACTION, audit.getAction());
        assertEquals("SUCCESS", audit.getResult());
        assertEquals(2L, audit.getOwnerId());
        assertNull(audit.getSourceAccountId());
        assertEquals(CUSTOMER_ACCOUNT, audit.getTargetAccountId());
        assertMoney(new BigDecimal(amount), audit.getAmount());
        assertNull(audit.getErrorMessage());
        assertNotNull(audit.getOperationId());
        assertNotNull(audit.getTimestamp());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidAmounts")
    void withdrawShouldReturn400AndPreserveBalancesWhenAmountIsInvalid(
            String scenario, String amountParam, BigDecimal expectedAuditAmount) {
        RequestSpecification request = given().auth().oauth2(customerToken());
        if (amountParam != null) {
            request.queryParam("amount", amountParam);
        }

        request.when().patch(WITHDRAW_PATH, CUSTOMER_ACCOUNT)
                .then()
                .statusCode(400)
                .body("error", equalTo(INVALID_AMOUNT_MESSAGE));

        assertOriginalBalances();
        assertWithdrawFailureAudit(2L, CUSTOMER_ACCOUNT, expectedAuditAmount, INVALID_AMOUNT_MESSAGE);
    }

    @Test
    void withdrawShouldReturn400WhenTheAccountBelongsToAnotherUser() {
        given()
                .auth().oauth2(customerToken())
                .queryParam("amount", "10.00")
                .when().patch(WITHDRAW_PATH, ACTIVE_THIRD_PARTY_ACCOUNT)
                .then()
                .statusCode(400)
                .body("error", equalTo("Usuário não é dono da conta ACC1004"));

        assertOriginalBalances();
        assertWithdrawFailureAudit(
                2L, ACTIVE_THIRD_PARTY_ACCOUNT, new BigDecimal("10.00"), "Usuário não é dono da conta ACC1004");
    }

    @Test
    void withdrawShouldReturn409WhenTheOwnAccountIsBlocked() {
        given()
                .auth().oauth2(token("customer_blocked", "blocked123"))
                .queryParam("amount", "10.00")
                .when().patch(WITHDRAW_PATH, BLOCKED_ACCOUNT)
                .then()
                .statusCode(409)
                .body("error", equalTo("Conta bloqueada: ACC1005"));

        assertOriginalBalances();
        assertWithdrawFailureAudit(5L, BLOCKED_ACCOUNT, new BigDecimal("10.00"), "Conta bloqueada: ACC1005");
    }

    @Test
    void withdrawShouldReturn409WhenBalanceIsInsufficient() {
        given()
                .auth().oauth2(customerToken())
                .queryParam("amount", "2750.51")
                .when().patch(WITHDRAW_PATH, CUSTOMER_ACCOUNT)
                .then()
                .statusCode(409)
                .body("error", equalTo("Saldo insuficiente."));

        assertOriginalBalances();
        assertWithdrawFailureAudit(2L, CUSTOMER_ACCOUNT, new BigDecimal("2750.51"), "Saldo insuficiente.");
    }

    @Test
    void withdrawShouldReturn400AndNotCreateAccountWhenAccountDoesNotExist() {
        given()
                .auth().oauth2(customerToken())
                .queryParam("amount", "10.00")
                .when().patch(WITHDRAW_PATH, MISSING_ACCOUNT)
                .then()
                .statusCode(400)
                .body("error", equalTo("Conta não encontrada: ACC9999"));

        assertEquals(0, accountsNamed(MISSING_ACCOUNT));
        assertEquals(accountCountBeforeTest, QuarkusTransaction.requiringNew().call(() -> accountRepository.count()));
        assertOriginalBalances();
        assertWithdrawFailureAudit(2L, MISSING_ACCOUNT, new BigDecimal("10.00"), "Conta não encontrada: ACC9999");
    }

    // ---------- transferência ----------

    @Test
    void transferShouldMoveFundsBetweenAccountsAndAuditTheSuccess() {
        Response response = given()
                .auth().oauth2(customerToken())
                .contentType(ContentType.JSON)
                .body(transferBody(ACTIVE_THIRD_PARTY_ACCOUNT, "25.50"))
                .when().patch(TRANSFER_PATH, CUSTOMER_ACCOUNT)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("message", equalTo("Transferência realizada com sucesso!"))
                .body("from", equalTo(CUSTOMER_ACCOUNT))
                .body("to", equalTo(ACTIVE_THIRD_PARTY_ACCOUNT))
                .extract().response();

        assertMoney(new BigDecimal("25.50"), bigDecimalFrom(response, "amount"));
        assertBalances(new BigDecimal("2725.00"), new BigDecimal("525.50"), BLOCKED_ACCOUNT_BALANCE);

        List<AccountAudit> audits = auditsFor(TRANSFER_ACTION);
        assertEquals(1, audits.size());
        AccountAudit audit = audits.get(0);
        assertEquals(TRANSFER_ACTION, audit.getAction());
        assertEquals("SUCCESS", audit.getResult());
        assertEquals(2L, audit.getOwnerId());
        assertEquals(CUSTOMER_ACCOUNT, audit.getSourceAccountId());
        assertEquals(ACTIVE_THIRD_PARTY_ACCOUNT, audit.getTargetAccountId());
        assertMoney(new BigDecimal("25.50"), audit.getAmount());
        assertNull(audit.getErrorMessage());
        assertNotNull(audit.getOperationId());
        assertNotNull(audit.getTimestamp());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidAmounts")
    void transferShouldReturn400AndPreserveBalancesWhenAmountIsInvalid(
            String scenario, String amountParam, BigDecimal expectedAuditAmount) {
        Map<String, Object> body = new HashMap<>();
        body.put("targetAccountId", ACTIVE_THIRD_PARTY_ACCOUNT);
        if (amountParam != null) {
            body.put("amount", new BigDecimal(amountParam));
        }

        given()
                .auth().oauth2(customerToken())
                .contentType(ContentType.JSON)
                .body(body)
                .when().patch(TRANSFER_PATH, CUSTOMER_ACCOUNT)
                .then()
                .statusCode(400)
                .body("error", equalTo(INVALID_AMOUNT_MESSAGE));

        assertOriginalBalances();
        assertTransferFailureAudit(
                2L, CUSTOMER_ACCOUNT, ACTIVE_THIRD_PARTY_ACCOUNT, expectedAuditAmount, INVALID_AMOUNT_MESSAGE);
    }

    @Test
    void transferShouldReturn400WhenSourceAndTargetAreTheSameAccount() {
        assertRejectedTransfer(customerToken(), CUSTOMER_ACCOUNT, CUSTOMER_ACCOUNT, "10.00", 400,
                2L, "Conta de origem e destino não podem ser iguais.");
    }

    @Test
    void transferShouldReturn400WhenSourceAccountBelongsToAnotherUser() {
        assertRejectedTransfer(customerToken(), ACTIVE_THIRD_PARTY_ACCOUNT, CUSTOMER_ACCOUNT, "10.00", 400,
                2L, "Usuário não é dono da conta ACC1004");
    }

    @Test
    void transferShouldReturn409WhenSourceAccountIsBlocked() {
        assertRejectedTransfer(token("customer_blocked", "blocked123"), BLOCKED_ACCOUNT,
                ACTIVE_THIRD_PARTY_ACCOUNT, "10.00", 409, 5L, "Conta bloqueada: ACC1005");
    }

    @Test
    void transferShouldReturn409WhenBalanceIsInsufficient() {
        assertRejectedTransfer(customerToken(), CUSTOMER_ACCOUNT, ACTIVE_THIRD_PARTY_ACCOUNT, "2750.51", 409,
                2L, "Saldo insuficiente.");
    }

    @Test
    void transferShouldReturn400AndNotCreateAccountWhenSourceAccountDoesNotExist() {
        assertRejectedTransfer(customerToken(), MISSING_ACCOUNT, ACTIVE_THIRD_PARTY_ACCOUNT, "10.00", 400,
                2L, "Conta não encontrada: ACC9999");
        assertAccountsWereNotCreated();
    }

    @Test
    void transferShouldReturn400AndPreserveSourceBalanceWhenTargetAccountDoesNotExist() {
        assertRejectedTransfer(customerToken(), CUSTOMER_ACCOUNT, MISSING_ACCOUNT, "10.00", 400,
                2L, "Conta não encontrada: ACC9999");
        assertAccountsWereNotCreated();
    }

    @Test
    void transferShouldReturn409WhenTargetAccountIsBlocked() {
        assertRejectedTransfer(customerToken(), CUSTOMER_ACCOUNT, BLOCKED_ACCOUNT, "10.00", 409,
                2L, "Conta bloqueada: ACC1005");
    }

    // ---------- criação de conta e consulta sem conta ----------

    @Test
    void createShouldPersistABlockedZeroBalanceAccountForTheNewCustomerAndAuditTheSuccess() {
        long ownerId = registerExclusiveUser();
        String token = token(EXCLUSIVE_USERNAME, EXCLUSIVE_PASSWORD);
        String expectedAccountId = "CC_" + ownerId;

        // o resource declara @Consumes(JSON); sem Content-Type o POST sem corpo falha com 500 (ver relatório)
        given()
                .auth().oauth2(token)
                .contentType(ContentType.JSON)
                .when().post(ACCOUNTS_PATH)
                .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("message", equalTo("Conta criada com sucesso!"));

        List<Account> accounts = accountsOwnedBy(ownerId);
        assertEquals(1, accounts.size());
        Account account = accounts.get(0);
        assertEquals(expectedAccountId, account.getAccountId());
        assertMoney(new BigDecimal("0"), account.getBalance());
        assertEquals(AccountStatusEnum.BLOCKED, account.getStatus());
        assertEquals(accountCountBeforeTest + 1, accountCount());
        assertOriginalBalances();

        List<AccountAudit> audits = newCreateAccountAudits();
        assertEquals(1, audits.size());
        AccountAudit audit = audits.get(0);
        assertEquals("SUCCESS", audit.getResult());
        assertEquals(ownerId, audit.getOwnerId());
        assertNull(audit.getSourceAccountId());
        assertEquals(expectedAccountId, audit.getTargetAccountId());
        assertNull(audit.getAmount());
        assertNull(audit.getErrorMessage());
        assertNotNull(audit.getOperationId());
        assertNotNull(audit.getTimestamp());

        long auditCountAfterCreate = auditCount();
        Response response = given()
                .auth().oauth2(token)
                .when().get(OWNER_PATH)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("id", equalTo(account.getId().intValue()))
                .body("accountId", equalTo(expectedAccountId))
                .body("ownerId", equalTo((int) ownerId))
                .body("status", equalTo("BLOCKED"))
                .extract().response();

        assertMoney(account.getBalance(), bigDecimalFrom(response, "balance"));
        assertEquals(accountCountBeforeTest + 1, accountCount());
        assertEquals(auditCountAfterCreate, auditCount());
    }

    @Test
    void createShouldReturn400WhenTheCustomerAlreadyHasAnAccount() {
        given()
                .auth().oauth2(customerToken())
                .contentType(ContentType.JSON)
                .when().post(ACCOUNTS_PATH)
                .then()
                .statusCode(400)
                .contentType(ContentType.JSON)
                .body("error", equalTo("Usuário id 2 já possui uma conta."));

        assertEquals(accountCountBeforeTest, accountCount());
        List<Account> accounts = accountsOwnedBy(2L);
        assertEquals(1, accounts.size());
        assertEquals(CUSTOMER_ACCOUNT, accounts.get(0).getAccountId());
        assertEquals(AccountStatusEnum.ACTIVE, accounts.get(0).getStatus());
        assertOriginalBalances();

        List<AccountAudit> audits = newCreateAccountAudits();
        assertEquals(1, audits.size());
        AccountAudit audit = audits.get(0);
        assertEquals("FAILURE", audit.getResult());
        // comportamento atual: a auditoria de falha de criação não registra ownerId nem contas
        assertNull(audit.getOwnerId());
        assertNull(audit.getSourceAccountId());
        assertNull(audit.getTargetAccountId());
        assertNull(audit.getAmount());
        assertEquals("Usuário id 2 já possui uma conta.", audit.getErrorMessage());
        assertNotNull(audit.getOperationId());
        assertNotNull(audit.getTimestamp());
    }

    @Test
    void getByOwnerShouldReturn404WhenTheCustomerHasNoAccount() {
        long ownerId = registerExclusiveUser();
        String token = token(EXCLUSIVE_USERNAME, EXCLUSIVE_PASSWORD);
        long auditCountBefore = auditCount();

        given()
                .auth().oauth2(token)
                .when().get(OWNER_PATH)
                .then()
                .statusCode(404)
                .contentType(ContentType.JSON)
                .body("error", equalTo("Nenhuma conta encontrada para o usuário " + ownerId));

        assertEquals(0, accountsOwnedBy(ownerId).size());
        assertEquals(accountCountBeforeTest, accountCount());
        assertOriginalBalances();
        assertEquals(auditCountBefore, auditCount());
        assertEquals(0, newCreateAccountAudits().size());
    }

    // ---------- helpers ----------

    private static long registerExclusiveUser() {
        return given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", EXCLUSIVE_USERNAME, "password", EXCLUSIVE_PASSWORD))
                .when().post(REGISTER_PATH)
                .then()
                .statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    private long accountCount() {
        return QuarkusTransaction.requiringNew().call(() -> accountRepository.count());
    }

    private long auditCount() {
        return QuarkusTransaction.requiringNew().call(() -> auditRepository.count());
    }

    private List<Account> accountsOwnedBy(long ownerId) {
        return QuarkusTransaction.requiringNew().call(() -> accountRepository.list("ownerId", ownerId));
    }

    private List<AccountAudit> newCreateAccountAudits() {
        return QuarkusTransaction.requiringNew().call(() ->
                auditRepository.list("action", CREATE_ACCOUNT_ACTION).stream()
                        .filter(audit -> !createAuditIdsBeforeTest.contains(audit.getId()))
                        .toList());
    }

    private static Map<String, Object> transferBody(String targetAccountId, String amount) {
        return Map.of("targetAccountId", targetAccountId, "amount", new BigDecimal(amount));
    }

    private void assertRejectedTransfer(String token, String source, String target, String amount,
                                        int expectedStatus, Long expectedOwnerId, String expectedMessage) {
        given()
                .auth().oauth2(token)
                .contentType(ContentType.JSON)
                .body(transferBody(target, amount))
                .when().patch(TRANSFER_PATH, source)
                .then()
                .statusCode(expectedStatus)
                .body("error", equalTo(expectedMessage));

        assertOriginalBalances();
        assertTransferFailureAudit(expectedOwnerId, source, target, new BigDecimal(amount), expectedMessage);
    }

    private void assertAccountsWereNotCreated() {
        assertEquals(0, accountsNamed(MISSING_ACCOUNT));
        assertEquals(accountCountBeforeTest, QuarkusTransaction.requiringNew().call(() -> accountRepository.count()));
    }

    private void assertTransferFailureAudit(Long expectedOwnerId, String source, String target,
                                            BigDecimal expectedAmount, String expectedMessage) {
        List<AccountAudit> audits = auditsFor(TRANSFER_ACTION);
        assertEquals(1, audits.size(), "Deve existir exatamente uma auditoria de transferência");
        AccountAudit audit = audits.get(0);
        assertEquals(TRANSFER_ACTION, audit.getAction());
        assertEquals("FAILURE", audit.getResult());
        assertEquals(expectedOwnerId, audit.getOwnerId());
        assertEquals(source, audit.getSourceAccountId());
        assertEquals(target, audit.getTargetAccountId());
        if (expectedAmount == null) {
            assertNull(audit.getAmount());
        } else {
            assertMoney(expectedAmount, audit.getAmount());
        }
        assertEquals(expectedMessage, audit.getErrorMessage());
        assertNotNull(audit.getOperationId());
        assertNotNull(audit.getTimestamp());
    }

    private static String customerToken() {
        return token("customer", "customer123");
    }

    private static String token(String username, String password) {
        return given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", username, "password", password))
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(200)
                .extract().path("token");
    }

    private static BigDecimal bigDecimalFrom(Response response, String path) {
        return response.jsonPath()
                .using(JsonPathConfig.jsonPathConfig()
                        .numberReturnType(NumberReturnType.BIG_DECIMAL))
                .getObject(path, BigDecimal.class);
    }

    private void restoreBalance(String accountId, BigDecimal balance) {
        accountRepository.update("balance = ?1 where accountId = ?2", balance, accountId);
    }

    private BigDecimal balanceOf(String accountId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Account account = accountRepository.find("accountId", accountId).firstResult();
            return account.getBalance();
        });
    }

    private long accountsNamed(String accountId) {
        return QuarkusTransaction.requiringNew().call(() -> accountRepository.count("accountId", accountId));
    }

    private List<AccountAudit> depositAudits() {
        return auditsFor(DEPOSIT_ACTION);
    }

    private List<AccountAudit> auditsFor(String action) {
        return QuarkusTransaction.requiringNew().call(() ->
                auditRepository.list("action = ?1 and targetAccountId in ?2", action, TOUCHED_ACCOUNTS));
    }

    private void assertBalances(BigDecimal customer, BigDecimal thirdParty, BigDecimal blocked) {
        assertMoney(customer, balanceOf(CUSTOMER_ACCOUNT));
        assertMoney(thirdParty, balanceOf(ACTIVE_THIRD_PARTY_ACCOUNT));
        assertMoney(blocked, balanceOf(BLOCKED_ACCOUNT));
        assertUntouchedAccounts();
    }

    private void assertOriginalBalances() {
        assertBalances(CUSTOMER_ACCOUNT_BALANCE, THIRD_PARTY_ACCOUNT_BALANCE, BLOCKED_ACCOUNT_BALANCE);
    }

    private void assertWithdrawFailureAudit(
            Long expectedOwnerId, String targetAccountId, BigDecimal expectedAmount, String expectedMessage) {
        List<AccountAudit> audits = auditsFor(WITHDRAW_ACTION);
        assertEquals(1, audits.size(), "Deve existir exatamente uma auditoria de saque");
        AccountAudit audit = audits.get(0);
        assertEquals(WITHDRAW_ACTION, audit.getAction());
        assertEquals("FAILURE", audit.getResult());
        assertEquals(expectedOwnerId, audit.getOwnerId());
        assertNull(audit.getSourceAccountId());
        assertEquals(targetAccountId, audit.getTargetAccountId());
        if (expectedAmount == null) {
            assertNull(audit.getAmount());
        } else {
            assertMoney(expectedAmount, audit.getAmount());
        }
        assertEquals(expectedMessage, audit.getErrorMessage());
        assertNotNull(audit.getOperationId());
        assertNotNull(audit.getTimestamp());
    }

    private void assertUntouchedAccounts() {
        UNTOUCHED_BALANCES.forEach((accountId, expected) -> assertMoney(expected, balanceOf(accountId)));
    }

    private void assertFailureAudit(String targetAccountId, BigDecimal expectedAmount, String expectedMessage) {
        List<AccountAudit> audits = depositAudits();
        assertEquals(1, audits.size(), "Deve existir exatamente uma auditoria de depósito");
        AccountAudit audit = audits.get(0);
        assertEquals(DEPOSIT_ACTION, audit.getAction());
        assertEquals("FAILURE", audit.getResult());
        assertEquals(targetAccountId, audit.getTargetAccountId());
        if (expectedAmount == null) {
            assertNull(audit.getAmount());
        } else {
            assertMoney(expectedAmount, audit.getAmount());
        }
        assertNull(audit.getSourceAccountId());
        assertEquals(expectedMessage, audit.getErrorMessage());
        assertNotNull(audit.getOperationId());
        assertNotNull(audit.getTimestamp());
    }

    private static void assertMoney(BigDecimal expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, expected.compareTo(actual), "esperado " + expected + " mas foi " + actual);
    }
}
