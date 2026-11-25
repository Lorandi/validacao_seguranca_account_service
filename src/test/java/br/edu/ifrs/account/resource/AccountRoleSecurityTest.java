package br.edu.ifrs.account.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AccountRoleSecurityTest {

    private static final String ACCOUNT_ID = "ACC1002";
    private static final String ADMIN_ACCOUNT_PATH = "/api/v1/accounts/" + ACCOUNT_ID;
    private static final String AUDIT_PATH = "/api/v1/audit/account/" + ACCOUNT_ID;
    private static final List<String> PROTECTED_DATA_MARKERS = List.of(ACCOUNT_ID, "balance", "operationId");

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    void adminShouldReadAccount() {
        // O administrador pode consultar os dados da conta.
        given()
                .when().get(ADMIN_ACCOUNT_PATH)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("accountId", equalTo(ACCOUNT_ID));
    }

    @Test
    @TestSecurity(user = "auditor", roles = "AUDITOR")
    void auditorShouldNotReadAccount() {
        // O auditor não pode consultar os dados da conta pela rota administrativa.
        assertForbidden(ADMIN_ACCOUNT_PATH);
    }

    @Test
    @TestSecurity(user = "customer", roles = "CUSTOMER")
    void customerShouldNotReadAccount() {
        // O cliente não pode usar a rota administrativa para consultar a conta.
        assertForbidden(ADMIN_ACCOUNT_PATH);
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    void adminShouldNotReadAudit() {
        // O perfil de administrador não dá acesso à auditoria.
        assertForbidden(AUDIT_PATH);
    }

    @Test
    @TestSecurity(user = "auditor", roles = "AUDITOR")
    void auditorShouldReadAudit() {
        // O auditor pode consultar o histórico, mesmo que a lista esteja vazia.
        given()
                .when().get(AUDIT_PATH)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", instanceOf(List.class));
    }

    @Test
    @TestSecurity(user = "customer", roles = "CUSTOMER")
    void customerShouldNotReadAudit() {
        // O cliente não pode consultar o histórico de auditoria.
        assertForbidden(AUDIT_PATH);
    }

    private static void assertForbidden(String path) {
        // Confere se a rota retorna 403 sem expor dados protegidos.
        String body = given()
                .when().get(path)
                .then()
                .statusCode(403)
                .extract().asString();

        PROTECTED_DATA_MARKERS.forEach(marker -> assertThat(body, not(containsString(marker))));
    }
}
