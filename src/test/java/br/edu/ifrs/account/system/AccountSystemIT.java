package br.edu.ifrs.account.system;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.config.JsonPathConfig;
import io.restassured.path.json.config.JsonPathConfig.NumberReturnType;
import io.restassured.response.Response;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Testes de sistema: exercitam o artefato empacotado como caixa-preta, somente por HTTP.
 */
@QuarkusIntegrationTest
class AccountSystemIT {

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String OWNER_ACCOUNT_PATH = "/api/v1/accounts/owner/";
    private static final String DEPOSIT_PATH = "/api/v1/accounts/{accountId}/deposit";
    private static final String WITHDRAW_PATH = "/api/v1/accounts/{accountId}/withdraw";
    private static final String TRANSFER_PATH = "/api/v1/accounts/{accountId}/transfer";
    private static final String AUDIT_BY_ACCOUNT_PATH = "/api/v1/audit/account/{accountId}";

    private static final String CUSTOMER_ACCOUNT = "ACC1002";
    private static final String ACTIVE_ACCOUNT = "ACC1004";

    @Test
    void customerShouldLoginConsultAccountAndDepositThroughPackagedApplication() {
        BigDecimal amount = new BigDecimal("10.00");
        String token = login("customer", "customer123");

        Response initial = fetchAccount(token);
        initial.then()
                .body("accountId", equalTo(CUSTOMER_ACCOUNT))
                .body("ownerId", equalTo(2))
                .body("status", equalTo("ACTIVE"));
        BigDecimal initialBalance = decimal(initial, "balance");

        Response deposit = deposit(token, CUSTOMER_ACCOUNT, amount);
        try {
            deposit.then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("message", equalTo("Depósito realizado com sucesso!"));
            assertMoney(initialBalance.add(amount), balanceOf(token));
        } finally {
            if (deposit.statusCode() == 200) {
                withdrawAndAssert(token, CUSTOMER_ACCOUNT, amount);
                assertMoney(initialBalance, balanceOf(token));
            }
        }
    }

    @Test
    void customerShouldWithdrawAndRestoreBalanceThroughPackagedApplication() {
        BigDecimal amount = new BigDecimal("17.25");
        String token = login("customer", "customer123");
        BigDecimal initialBalance = balanceOf(token);

        Response withdraw = withdraw(token, CUSTOMER_ACCOUNT, amount);
        try {
            withdraw.then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("message", equalTo("Saque realizado com sucesso!"));
            assertMoney(initialBalance.subtract(amount), balanceOf(token));
        } finally {
            if (withdraw.statusCode() == 200) {
                depositAndAssert(token, CUSTOMER_ACCOUNT, amount);
                assertMoney(initialBalance, balanceOf(token));
            }
        }
    }

    @Test
    void customerShouldTransferFundsAndAuditorShouldFindTheOperation() {
        BigDecimal amount = new BigDecimal("25.50");
        String customerToken = login("customer", "customer123");
        String activeToken = login("customer_active", "active123");
        String auditorToken = login("auditor", "auditor123");
        BigDecimal sourceInitial = balanceOf(customerToken);
        BigDecimal targetInitial = balanceOf(activeToken);

        Response transfer = transfer(customerToken, CUSTOMER_ACCOUNT, ACTIVE_ACCOUNT, amount);
        try {
            transfer.then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("message", equalTo("Transferência realizada com sucesso!"))
                    .body("from", equalTo(CUSTOMER_ACCOUNT))
                    .body("to", equalTo(ACTIVE_ACCOUNT));
            assertMoney(amount, decimal(transfer, "amount"));
            assertMoney(sourceInitial.subtract(amount), balanceOf(customerToken));
            assertMoney(targetInitial.add(amount), balanceOf(activeToken));

            Response audit = given()
                    .auth().oauth2(auditorToken)
                    .when()
                    .get(AUDIT_BY_ACCOUNT_PATH, CUSTOMER_ACCOUNT);
            audit.then().statusCode(200).contentType(ContentType.JSON);
            Map<String, Object> record = findTransferAudit(audit, amount);
            Object operationId = record.get("operationId");
            assertNotNull(operationId, "operationId não deveria ser nulo");
            assertFalse(operationId.toString().isBlank(), "operationId não deveria estar em branco");
            assertNotNull(record.get("timestamp"));
            assertNull(record.get("errorMessage"));
        } finally {
            if (transfer.statusCode() == 200) {
                Response reverse = transfer(activeToken, ACTIVE_ACCOUNT, CUSTOMER_ACCOUNT, amount);
                reverse.then()
                        .statusCode(200)
                        .contentType(ContentType.JSON)
                        .body("from", equalTo(ACTIVE_ACCOUNT))
                        .body("to", equalTo(CUSTOMER_ACCOUNT));
                assertMoney(amount, decimal(reverse, "amount"));
                assertMoney(sourceInitial, balanceOf(customerToken));
                assertMoney(targetInitial, balanceOf(activeToken));
            }
        }
    }

    private static String login(String username, String password) {
        return given()
                .contentType(ContentType.JSON)
                .body(String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password))
                .when()
                .post(LOGIN_PATH)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("token", matchesPattern("^[\\w-]+\\.[\\w-]+\\.[\\w-]+$"))
                .body("tokenType", equalTo("Bearer"))
                .body("expiresIn", equalTo(3600))
                .extract()
                .path("token");
    }

    private static Response fetchAccount(String token) {
        return given()
                .auth().oauth2(token)
                .when()
                .get(OWNER_ACCOUNT_PATH)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .response();
    }

    private static BigDecimal balanceOf(String token) {
        return decimal(fetchAccount(token), "balance");
    }

    private static Response deposit(String token, String accountId, BigDecimal amount) {
        return given()
                .auth().oauth2(token)
                .when()
                .patch(DEPOSIT_PATH + "?amount=" + amount.toPlainString(), accountId);
    }

    private static Response withdraw(String token, String accountId, BigDecimal amount) {
        return given()
                .auth().oauth2(token)
                .when()
                .patch(WITHDRAW_PATH + "?amount=" + amount.toPlainString(), accountId);
    }

    private static void depositAndAssert(String token, String accountId, BigDecimal amount) {
        deposit(token, accountId, amount).then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("message", equalTo("Depósito realizado com sucesso!"));
    }

    private static void withdrawAndAssert(String token, String accountId, BigDecimal amount) {
        withdraw(token, accountId, amount).then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("message", equalTo("Saque realizado com sucesso!"));
    }

    private static Response transfer(String token, String from, String to, BigDecimal amount) {
        return given()
                .auth().oauth2(token)
                .contentType(ContentType.JSON)
                .body(String.format("{\"targetAccountId\":\"%s\",\"amount\":%s}", to, amount.toPlainString()))
                .when()
                .patch(TRANSFER_PATH, from);
    }

    private static Map<String, Object> findTransferAudit(Response audit, BigDecimal amount) {
        List<Map<String, Object>> records = audit.jsonPath()
                .using(JsonPathConfig.jsonPathConfig().numberReturnType(NumberReturnType.BIG_DECIMAL))
                .getList("$");
        List<Map<String, Object>> matches = records.stream()
                .filter(r -> "TRANSFER".equals(r.get("action"))
                        && "SUCCESS".equals(r.get("result"))
                        && Long.valueOf(2L).equals(toLong(r.get("ownerId")))
                        && CUSTOMER_ACCOUNT.equals(r.get("sourceAccountId"))
                        && ACTIVE_ACCOUNT.equals(r.get("targetAccountId"))
                        && r.get("amount") != null
                        && amount.compareTo(new BigDecimal(r.get("amount").toString())) == 0)
                .toList();
        assertTrue(!matches.isEmpty(), "Nenhum registro de auditoria da transferência encontrado em " + records);
        return matches.get(0);
    }

    private static Long toLong(Object value) {
        return value == null ? null : Long.valueOf(value.toString());
    }

    private static BigDecimal decimal(Response response, String path) {
        return response.jsonPath()
                .using(JsonPathConfig.jsonPathConfig().numberReturnType(NumberReturnType.BIG_DECIMAL))
                .getObject(path, BigDecimal.class);
    }

    private static void assertMoney(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual), "Esperado " + expected + " mas foi " + actual);
    }
}
