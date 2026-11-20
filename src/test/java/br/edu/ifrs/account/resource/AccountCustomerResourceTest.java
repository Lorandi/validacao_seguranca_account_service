package br.edu.ifrs.account.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import br.edu.ifrs.account.entity.Account;
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
import java.util.List;
import java.util.Map;
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

    private static final String DEPOSIT_ACTION = "DEPOSIT";
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

    private long accountCountBeforeTest;

    @BeforeEach
    void prepareData() {
        restoreData();
        accountCountBeforeTest = QuarkusTransaction.requiringNew().call(() -> accountRepository.count());
    }

    @AfterEach
    void restoreData() {
        QuarkusTransaction.requiringNew().run(() -> {
            auditRepository.delete("action = ?1 and targetAccountId in ?2", DEPOSIT_ACTION, TOUCHED_ACCOUNTS);
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

    // ---------- helpers ----------

    private static String customerToken() {
        return given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", "customer", "password", "customer123"))
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
        return QuarkusTransaction.requiringNew().call(() ->
                auditRepository.list("action = ?1 and targetAccountId in ?2", DEPOSIT_ACTION, TOUCHED_ACCOUNTS));
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
