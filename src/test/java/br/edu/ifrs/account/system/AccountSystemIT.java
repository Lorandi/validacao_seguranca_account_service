package br.edu.ifrs.account.system;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.config.JsonPathConfig;
import io.restassured.path.json.config.JsonPathConfig.NumberReturnType;
import io.restassured.response.Response;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Teste de sistema: exercita o artefato empacotado como caixa-preta, somente por HTTP.
 */
@QuarkusIntegrationTest
class AccountSystemIT {

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String OWNER_ACCOUNT_PATH = "/api/v1/accounts/owner/";
    private static final String DEPOSIT_PATH = "/api/v1/accounts/{accountId}/deposit";
    private static final String WITHDRAW_PATH = "/api/v1/accounts/{accountId}/withdraw";
    private static final String AMOUNT = "10.00";

    @Test
    void customerShouldLoginConsultAccountAndDepositThroughPackagedApplication() {
        String token = given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"customer\",\"password\":\"customer123\"}")
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

        Response initial = fetchAccount(token);
        initial.then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("accountId", equalTo("ACC1002"))
                .body("ownerId", equalTo(2))
                .body("status", equalTo("ACTIVE"));
        String accountId = initial.jsonPath().getString("accountId");
        BigDecimal initialBalance = balanceOf(initial);
        BigDecimal amount = new BigDecimal(AMOUNT);

        given()
                .auth().oauth2(token)
                .when()
                .patch(DEPOSIT_PATH + "?amount=" + AMOUNT, accountId)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("message", equalTo("Depósito realizado com sucesso!"));

        try {
            BigDecimal afterDeposit = balanceOf(fetchAccount(token));
            assertEquals(0, initialBalance.add(amount).compareTo(afterDeposit),
                    "Saldo após o depósito deveria ser o inicial + 10,00, mas foi " + afterDeposit);
        } finally {
            given()
                    .auth().oauth2(token)
                    .when()
                    .patch(WITHDRAW_PATH + "?amount=" + AMOUNT, accountId)
                    .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("message", equalTo("Saque realizado com sucesso!"));
        }

        BigDecimal restored = balanceOf(fetchAccount(token));
        assertEquals(0, initialBalance.compareTo(restored),
                "Saldo deveria ter voltado a " + initialBalance + ", mas foi " + restored);
    }

    private static Response fetchAccount(String token) {
        return given()
                .auth().oauth2(token)
                .when()
                .get(OWNER_ACCOUNT_PATH);
    }

    private static BigDecimal balanceOf(Response response) {
        return response.jsonPath()
                .using(JsonPathConfig.jsonPathConfig()
                        .numberReturnType(NumberReturnType.BIG_DECIMAL))
                .getObject("balance", BigDecimal.class);
    }
}
