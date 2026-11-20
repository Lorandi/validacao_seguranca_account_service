package br.edu.ifrs.account.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

@QuarkusTest
class AuthorizationTest {

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final List<String> PROTECTED_DATA_MARKERS = List.of("ACC1", "balance", "operationId");

    private enum Profile {
        CUSTOMER("customer", "customer123", "/api/v1/accounts/owner/"),
        ADMIN("admin", "admin123", "/api/v1/accounts"),
        AUDITOR("auditor", "auditor123", "/api/v1/audit");

        private final String username;
        private final String password;
        private final String endpoint;

        Profile(String username, String password, String endpoint) {
            this.username = username;
            this.password = password;
            this.endpoint = endpoint;
        }
    }

    static Stream<Arguments> forbiddenAccesses() {
        return Stream.of(Profile.values())
                .flatMap(caller -> Stream.of(Profile.values())
                        .filter(target -> target != caller)
                        .map(target -> Arguments.of(caller, target)));
    }

    @ParameterizedTest(name = "sem token em {0}")
    @EnumSource(Profile.class)
    void protectedEndpointShouldReturn401WhenTokenIsMissing(Profile target) {
        String body = given()
                .when().get(target.endpoint)
                .then()
                .statusCode(401)
                .extract().asString();

        assertNoProtectedData(body);
    }

    @ParameterizedTest(name = "token malformado em {0}")
    @EnumSource(Profile.class)
    void protectedEndpointShouldReturn401WhenTokenIsMalformed(Profile target) {
        String body = given()
                .header("Authorization", "Bearer token-invalido")
                .when().get(target.endpoint)
                .then()
                .statusCode(401)
                .extract().asString();

        assertNoProtectedData(body);
    }

    @ParameterizedTest(name = "{0} acessa o próprio endpoint")
    @EnumSource(Profile.class)
    void protectedEndpointShouldReturn200ForTheCorrectProfile(Profile profile) {
        given()
                .auth().oauth2(tokenFor(profile))
                .when().get(profile.endpoint)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", instanceOfFor(profile));
    }

    @Test
    void customerEndpointShouldReturnTheCustomerAccount() {
        given()
                .auth().oauth2(tokenFor(Profile.CUSTOMER))
                .when().get(Profile.CUSTOMER.endpoint)
                .then()
                .statusCode(200)
                .body("accountId", equalTo("ACC1002"));
    }

    @ParameterizedTest(name = "{0} em endpoint de {1}")
    @MethodSource("forbiddenAccesses")
    void protectedEndpointShouldReturn403ForAValidTokenWithoutTheRequiredProfile(Profile caller, Profile target) {
        String body = given()
                .auth().oauth2(tokenFor(caller))
                .when().get(target.endpoint)
                .then()
                .statusCode(403)
                .extract().asString();

        assertNoProtectedData(body);
    }

    private static Matcher<Object> instanceOfFor(Profile profile) {
        return profile == Profile.CUSTOMER ? instanceOf(Map.class) : instanceOf(List.class);
    }

    private static String tokenFor(Profile profile) {
        return given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", profile.username, "password", profile.password))
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(200)
                .extract().path("token");
    }

    private static void assertNoProtectedData(String body) {
        PROTECTED_DATA_MARKERS.forEach(marker -> assertThat(body, not(containsString(marker))));
    }
}
