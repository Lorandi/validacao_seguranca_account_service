package br.edu.ifrs.account.system;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.config.JsonPathConfig;
import io.restassured.path.json.config.JsonPathConfig.NumberReturnType;
import io.restassured.response.Response;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Testes de sistema de administração e segurança: artefato empacotado como caixa-preta, somente por HTTP.
 */
@QuarkusIntegrationTest
class AccountAdminSecurityIT {

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String ACCOUNTS_PATH = "/api/v1/accounts";
    private static final String ADMIN_ACCOUNT_PATH = "/api/v1/accounts/{accountId}";
    private static final String OWNER_ACCOUNT_PATH = "/api/v1/accounts/owner/";
    private static final String TOGGLE_PATH = "/api/v1/accounts/{accountId}/account-status-toggle";
    private static final String DEPOSIT_PATH = "/api/v1/accounts/{accountId}/deposit";
    private static final String WITHDRAW_PATH = "/api/v1/accounts/{accountId}/withdraw";

    private static final String ACCOUNT = "ACC1004";
    private static final String UUID_PATTERN =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    @Test
    void adminShouldBlockRejectOperationUnblockAndAllowOperation() {
        BigDecimal amount = new BigDecimal("13.75");
        String adminToken = login("admin", "admin123");
        String ownerToken = login("customer_active", "active123");

        Response adminView = adminAccount(adminToken);
        Response ownerView = ownerAccount(ownerToken);
        adminView.then()
                .body("accountId", equalTo(ACCOUNT))
                .body("ownerId", equalTo(4))
                .body("status", equalTo("ACTIVE"));
        ownerView.then()
                .body("accountId", equalTo(ACCOUNT))
                .body("ownerId", equalTo(4))
                .body("status", equalTo("ACTIVE"));
        BigDecimal initialBalance = decimal(adminView, "balance");
        assertMoney(initialBalance, decimal(ownerView, "balance"));

        try {
            toggle(adminToken).then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("message", equalTo("Status da conta alterado com sucesso!"))
                    .body("accountId", equalTo(ACCOUNT))
                    .body("status", equalTo("BLOCKED"));
            Response blocked = adminAccount(adminToken);
            blocked.then().body("status", equalTo("BLOCKED")).body("ownerId", equalTo(4));
            assertMoney(initialBalance, decimal(blocked, "balance"));

            withdraw(ownerToken, amount).then()
                    .statusCode(409)
                    .contentType(ContentType.JSON)
                    .body("error", equalTo("Conta bloqueada: " + ACCOUNT));
            Response afterRejection = adminAccount(adminToken);
            afterRejection.then().body("status", equalTo("BLOCKED"));
            assertMoney(initialBalance, decimal(afterRejection, "balance"));

            toggle(adminToken).then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("accountId", equalTo(ACCOUNT))
                    .body("status", equalTo("ACTIVE"));
            Response unblocked = adminAccount(adminToken);
            unblocked.then().body("status", equalTo("ACTIVE"));
            assertMoney(initialBalance, decimal(unblocked, "balance"));

            withdraw(ownerToken, amount).then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("message", equalTo("Saque realizado com sucesso!"));
            assertMoney(initialBalance.subtract(amount), decimal(ownerAccount(ownerToken), "balance"));
        } finally {
            restore(adminToken, ownerToken, initialBalance, amount);
        }
    }

    @Test
    void loginShouldReturn401ForWrongPasswordThroughPackagedApplication() {
        String wrongPassword = "senha-incorreta";

        Response response = given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"customer\",\"password\":\"" + wrongPassword + "\"}")
                .when()
                .post(LOGIN_PATH)
                .then()
                .statusCode(401)
                .contentType(ContentType.JSON)
                .body("status", equalTo(401))
                .body("error", equalTo("Credenciais inválidas."))
                .body("errorId", notNullValue())
                .body("errorId", matchesPattern(UUID_PATTERN))
                .body("token", nullValue())
                .extract()
                .response();

        String body = response.asString();
        assertFalse(body.contains(wrongPassword), "A resposta não deveria conter a senha enviada");
        assertFalse(body.contains("passwordHash"), "A resposta não deveria conter passwordHash");
    }

    @Test
    void customerShouldReceive403WhenAccessingAdministrativeEndpoint() {
        String customerToken = login("customer", "customer123");

        Response response = given()
                .auth().oauth2(customerToken)
                .when()
                .get(ACCOUNTS_PATH)
                .then()
                .statusCode(403)
                .extract()
                .response();

        String body = response.asString();
        for (String forbidden : new String[] {"ACC", "balance", "passwordHash", "token", "password"}) {
            assertFalse(body.contains(forbidden), "O corpo do 403 não deveria conter '" + forbidden + "'");
        }
    }

    private static void restore(String adminToken, String ownerToken, BigDecimal initialBalance, BigDecimal amount) {
        Response current = adminAccount(adminToken);
        if ("BLOCKED".equals(current.jsonPath().getString("status"))) {
            toggle(adminToken).then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("status", equalTo("ACTIVE"));
        }

        BigDecimal balance = decimal(ownerAccount(ownerToken), "balance");
        if (balance.compareTo(initialBalance.subtract(amount)) == 0) {
            deposit(ownerToken, amount).then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("message", equalTo("Depósito realizado com sucesso!"));
        } else if (balance.compareTo(initialBalance) != 0) {
            fail("Saldo inesperado de " + ACCOUNT + " na restauração: " + balance
                    + " (inicial " + initialBalance + ")");
        }

        Response restored = adminAccount(adminToken);
        restored.then()
                .body("status", equalTo("ACTIVE"))
                .body("ownerId", equalTo(4));
        assertMoney(initialBalance, decimal(restored, "balance"));
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

    private static Response adminAccount(String adminToken) {
        return given()
                .auth().oauth2(adminToken)
                .when()
                .get(ADMIN_ACCOUNT_PATH, ACCOUNT)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .response();
    }

    private static Response ownerAccount(String ownerToken) {
        return given()
                .auth().oauth2(ownerToken)
                .when()
                .get(OWNER_ACCOUNT_PATH)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .response();
    }

    private static Response toggle(String adminToken) {
        return given()
                .auth().oauth2(adminToken)
                .contentType(ContentType.JSON)
                .when()
                .patch(TOGGLE_PATH, ACCOUNT);
    }

    private static Response deposit(String token, BigDecimal amount) {
        return given()
                .auth().oauth2(token)
                .when()
                .patch(DEPOSIT_PATH + "?amount=" + amount.toPlainString(), ACCOUNT);
    }

    private static Response withdraw(String token, BigDecimal amount) {
        return given()
                .auth().oauth2(token)
                .when()
                .patch(WITHDRAW_PATH + "?amount=" + amount.toPlainString(), ACCOUNT);
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
