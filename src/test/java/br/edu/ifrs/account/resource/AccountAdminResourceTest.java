package br.edu.ifrs.account.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import br.edu.ifrs.account.entity.Account;
import br.edu.ifrs.account.entity.AccountAudit;
import br.edu.ifrs.account.enums.AccountStatusEnum;
import br.edu.ifrs.account.repository.AccountAuditRepository;
import br.edu.ifrs.account.repository.AccountRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.config.JsonPathConfig;
import io.restassured.path.json.config.JsonPathConfig.NumberReturnType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AccountAdminResourceTest {

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String ACCOUNTS_PATH = "/api/v1/accounts";
    private static final String ACCOUNT_PATH = "/api/v1/accounts/{accountId}";
    private static final String TOGGLE_PATH = "/api/v1/accounts/{accountId}/account-status-toggle";

    private static final String CUSTOMER_ACCOUNT = "ACC1002";
    private static final String ACTIVE_ACCOUNT = "ACC1004";
    private static final String BLOCKED_ACCOUNT = "ACC1005";
    private static final String MISSING_ACCOUNT = "ACC9999";

    private static final String TOGGLE_ACTION = "TOGGLE_STATUS";
    private static final List<String> TOGGLE_TARGETS = List.of(ACTIVE_ACCOUNT, BLOCKED_ACCOUNT, MISSING_ACCOUNT);

    private record AccountSnapshot(Long id, String accountId, BigDecimal balance, Long ownerId,
                                   AccountStatusEnum status) {
    }

    @Inject
    AccountRepository accountRepository;

    @Inject
    AccountAuditRepository auditRepository;

    private Map<String, AccountSnapshot> accountsBeforeTest;
    private Set<Long> toggleAuditIdsBeforeTest;
    private long auditCountBeforeTest;

    @BeforeEach
    void prepareData() {
        restoreStatuses();
        accountsBeforeTest = snapshotAccounts();
        toggleAuditIdsBeforeTest = QuarkusTransaction.requiringNew().call(() ->
                auditRepository.list("action", TOGGLE_ACTION).stream()
                        .map(AccountAudit::getId)
                        .collect(Collectors.toSet()));
        auditCountBeforeTest = auditCount();
    }

    @AfterEach
    void restoreData() {
        if (toggleAuditIdsBeforeTest != null) {
            // remove somente as auditorias de alternância criadas pelo teste corrente
            QuarkusTransaction.requiringNew().run(() ->
                    auditRepository.list("action = ?1 and targetAccountId in ?2", TOGGLE_ACTION, TOGGLE_TARGETS)
                            .stream()
                            .filter(audit -> !toggleAuditIdsBeforeTest.contains(audit.getId()))
                            .forEach(auditRepository::delete));
        }
        restoreStatuses();
    }

    // ---------- consultas ----------

    @Test
    void listAllShouldReturnEveryPersistedAccountWithoutModifyingTheDatabase() {
        Response response = given()
                .auth().oauth2(adminToken())
                .when().get(ACCOUNTS_PATH)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", equalTo(accountsBeforeTest.size()))
                .body("accountId", hasItems("ACC1001", CUSTOMER_ACCOUNT, "ACC1003", "ACC2001",
                        ACTIVE_ACCOUNT, BLOCKED_ACCOUNT))
                .body("find { it.accountId == 'ACC1002' }.ownerId", equalTo(2))
                .body("find { it.accountId == 'ACC1002' }.status", equalTo("ACTIVE"))
                .extract().response();

        assertMoney(new BigDecimal("2750.50"),
                bigDecimalFrom(response, "find { it.accountId == 'ACC1002' }.balance"));
        assertAccountsUnchangedExcept(null, null);
        assertEquals(auditCountBeforeTest, auditCount());
    }

    @Test
    void getByAccountIdShouldReturnTheExistingAccountWithoutModifyingTheDatabase() {
        AccountSnapshot persisted = accountsBeforeTest.get(CUSTOMER_ACCOUNT);

        Response response = given()
                .auth().oauth2(adminToken())
                .when().get(ACCOUNT_PATH, CUSTOMER_ACCOUNT)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("id", equalTo(persisted.id().intValue()))
                .body("accountId", equalTo(CUSTOMER_ACCOUNT))
                .body("ownerId", equalTo(2))
                .body("status", equalTo("ACTIVE"))
                .extract().response();

        assertMoney(new BigDecimal("2750.50"), bigDecimalFrom(response, "balance"));
        assertAccountsUnchangedExcept(null, null);
        assertEquals(auditCountBeforeTest, auditCount());
    }

    @Test
    void getByAccountIdShouldReturn404WhenTheAccountDoesNotExist() {
        given()
                .auth().oauth2(adminToken())
                .when().get(ACCOUNT_PATH, MISSING_ACCOUNT)
                .then()
                .statusCode(404)
                .contentType(ContentType.JSON)
                .body("error", equalTo("Conta não encontrada: ACC9999"));

        assertAccountsUnchangedExcept(null, null);
        assertEquals(auditCountBeforeTest, auditCount());
    }

    // ---------- alternância de status ----------

    @Test
    void toggleStatusShouldBlockAnActiveAccountAndAuditTheSuccess() {
        assertEquals(AccountStatusEnum.ACTIVE, accountsBeforeTest.get(ACTIVE_ACCOUNT).status());

        assertSuccessfulToggle(ACTIVE_ACCOUNT, AccountStatusEnum.BLOCKED, 4L);
    }

    @Test
    void toggleStatusShouldUnblockABlockedAccountAndAuditTheSuccess() {
        assertEquals(AccountStatusEnum.BLOCKED, accountsBeforeTest.get(BLOCKED_ACCOUNT).status());

        assertSuccessfulToggle(BLOCKED_ACCOUNT, AccountStatusEnum.ACTIVE, 5L);
    }

    @Test
    void toggleStatusShouldReturn404AndAuditTheFailureWhenTheAccountDoesNotExist() {
        given()
                .auth().oauth2(adminToken())
                .contentType(ContentType.JSON)
                .when().patch(TOGGLE_PATH, MISSING_ACCOUNT)
                .then()
                .statusCode(404)
                .contentType(ContentType.JSON)
                .body("error", equalTo("Conta não encontrada: ACC9999"));

        assertAccountsUnchangedExcept(null, null);

        List<AccountAudit> audits = newToggleAudits();
        assertEquals(1, audits.size());
        AccountAudit audit = audits.get(0);
        assertEquals(TOGGLE_ACTION, audit.getAction());
        assertEquals("FAILURE", audit.getResult());
        assertNull(audit.getOwnerId());
        assertNull(audit.getSourceAccountId());
        assertEquals(MISSING_ACCOUNT, audit.getTargetAccountId());
        assertNull(audit.getAmount());
        assertEquals("Conta não encontrada: ACC9999", audit.getErrorMessage());
        assertNotNull(audit.getOperationId());
        assertNotNull(audit.getTimestamp());
    }

    // ---------- helpers ----------

    private void assertSuccessfulToggle(String accountId, AccountStatusEnum expectedStatus, Long expectedOwnerId) {
        given()
                .auth().oauth2(adminToken())
                .contentType(ContentType.JSON)
                .when().patch(TOGGLE_PATH, accountId)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("message", equalTo("Status da conta alterado com sucesso!"))
                .body("accountId", equalTo(accountId))
                .body("status", equalTo(expectedStatus.name()));

        // somente o status da conta solicitada pode mudar; saldo, ownerId e demais contas ficam iguais
        assertAccountsUnchangedExcept(accountId, expectedStatus);

        List<AccountAudit> audits = newToggleAudits();
        assertEquals(1, audits.size());
        AccountAudit audit = audits.get(0);
        assertEquals(TOGGLE_ACTION, audit.getAction());
        assertEquals("SUCCESS", audit.getResult());
        assertEquals(expectedOwnerId, audit.getOwnerId());
        assertNull(audit.getSourceAccountId());
        assertEquals(accountId, audit.getTargetAccountId());
        assertNull(audit.getAmount());
        assertNull(audit.getErrorMessage());
        assertNotNull(audit.getOperationId());
        assertNotNull(audit.getTimestamp());
    }

    private static String adminToken() {
        return given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", "admin", "password", "admin123"))
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(200)
                .extract().path("token");
    }

    private static BigDecimal bigDecimalFrom(Response response, String path) {
        return response.jsonPath()
                .using(JsonPathConfig.jsonPathConfig().numberReturnType(NumberReturnType.BIG_DECIMAL))
                .getObject(path, BigDecimal.class);
    }

    private void restoreStatuses() {
        QuarkusTransaction.requiringNew().run(() -> {
            accountRepository.update("status = ?1 where accountId = ?2", AccountStatusEnum.ACTIVE, ACTIVE_ACCOUNT);
            accountRepository.update("status = ?1 where accountId = ?2", AccountStatusEnum.BLOCKED, BLOCKED_ACCOUNT);
        });
    }

    private Map<String, AccountSnapshot> snapshotAccounts() {
        return QuarkusTransaction.requiringNew().call(() -> accountRepository.listAll().stream()
                .map(a -> new AccountSnapshot(a.getId(), a.getAccountId(), a.getBalance(), a.getOwnerId(),
                        a.getStatus()))
                .collect(Collectors.toMap(AccountSnapshot::accountId, Function.identity())));
    }

    private long auditCount() {
        return QuarkusTransaction.requiringNew().call(() -> auditRepository.count());
    }

    private List<AccountAudit> newToggleAudits() {
        return QuarkusTransaction.requiringNew().call(() ->
                auditRepository.list("action", TOGGLE_ACTION).stream()
                        .filter(audit -> !toggleAuditIdsBeforeTest.contains(audit.getId()))
                        .toList());
    }

    /**
     * Compara o banco com o snapshot inicial: mesma quantidade de contas e mesmos id, ownerId e saldo;
     * o status só pode diferir na conta {@code changedAccountId}, que deve ter {@code changedStatus}.
     */
    private void assertAccountsUnchangedExcept(String changedAccountId, AccountStatusEnum changedStatus) {
        Map<String, AccountSnapshot> after = snapshotAccounts();

        assertEquals(accountsBeforeTest.keySet(), after.keySet());
        accountsBeforeTest.forEach((accountId, before) -> {
            AccountSnapshot current = after.get(accountId);
            assertEquals(before.id(), current.id(), accountId);
            assertEquals(before.ownerId(), current.ownerId(), accountId);
            assertMoney(before.balance(), current.balance());
            AccountStatusEnum expectedStatus = accountId.equals(changedAccountId) ? changedStatus : before.status();
            assertEquals(expectedStatus, current.status(), "status de " + accountId);
        });
    }

    private static void assertMoney(BigDecimal expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, expected.compareTo(actual), "esperado " + expected + " mas foi " + actual);
    }
}
