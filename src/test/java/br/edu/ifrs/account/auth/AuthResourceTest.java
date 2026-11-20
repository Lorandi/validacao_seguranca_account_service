package br.edu.ifrs.account.auth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mindrot.jbcrypt.BCrypt;

@QuarkusTest
class AuthResourceTest {

    private static final String REGISTER_PATH = "/api/v1/auth/register";
    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String TEST_USERNAME = "component_auth_user";
    private static final String VALID_PASSWORD = "senha-segura-123";
    private static final String DEMO_USERNAME = "customer";
    private static final String DEMO_PASSWORD = "customer123";
    private static final String JWT_PATTERN = "^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$";

    @Inject
    UserRepository repository;

    @BeforeEach
    @AfterEach
    void removeTestUser() {
        QuarkusTransaction.requiringNew().run(() -> repository.delete("username", TEST_USERNAME));
    }

    static Stream<Arguments> invalidRegistrations() {
        return Stream.of(
                Arguments.of("username ausente", body(null, VALID_PASSWORD), "username"),
                Arguments.of("username em branco", body("   ", VALID_PASSWORD), "username"),
                Arguments.of("username com 2 caracteres", body("ab", VALID_PASSWORD), "username"),
                Arguments.of("username com 51 caracteres", body("u".repeat(51), VALID_PASSWORD), "username"),
                Arguments.of("password ausente", body(TEST_USERNAME, null), "password"),
                Arguments.of("password em branco", body(TEST_USERNAME, "        "), "password"),
                Arguments.of("password com 7 caracteres", body(TEST_USERNAME, "1234567"), "password"),
                Arguments.of("password com 73 caracteres", body(TEST_USERNAME, "p".repeat(73)), "password"));
    }

    static Stream<Arguments> invalidLogins() {
        return Stream.of(
                Arguments.of("username ausente", body(null, DEMO_PASSWORD), "username"),
                Arguments.of("username em branco", body("   ", DEMO_PASSWORD), "username"),
                Arguments.of("password ausente", body(DEMO_USERNAME, null), "password"),
                Arguments.of("password em branco", body(DEMO_USERNAME, "   "), "password"));
    }

    // ---------- cadastro ----------

    @Test
    void registerShouldCreateACustomerPersistItWithBcryptHashAndNotExposeTheCredentials() {
        String raw = given()
                .contentType(ContentType.JSON)
                .body(body("  " + TEST_USERNAME + "  ", VALID_PASSWORD))
                .when().post(REGISTER_PATH)
                .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("id", greaterThan(0))
                .body("username", equalTo(TEST_USERNAME))
                .body("role", equalTo("CUSTOMER"))
                .extract().asString();

        assertFalse(raw.contains(VALID_PASSWORD));
        assertFalse(raw.contains("passwordHash"));
        assertFalse(raw.contains("$2a$"));

        List<User> users = findUsers(TEST_USERNAME);
        assertEquals(1, users.size());
        User persisted = users.get(0);
        assertEquals(UserRole.CUSTOMER, persisted.getRole());
        assertFalse(VALID_PASSWORD.equals(persisted.getPasswordHash()));
        assertTrue(BCrypt.checkpw(VALID_PASSWORD, persisted.getPasswordHash()));
    }

    @Test
    void registerShouldRejectAnAlreadyRegisteredUsernameWithoutPersistingAnotherUser() {
        long usersBefore = countUsers();

        given()
                .contentType(ContentType.JSON)
                .body(body(DEMO_USERNAME, VALID_PASSWORD))
                .when().post(REGISTER_PATH)
                .then()
                .statusCode(400)
                .body("status", equalTo(400))
                .body("error", equalTo("Nome de usuário já cadastrado."))
                .body("errorId", notNullValue());

        assertEquals(1, findUsers(DEMO_USERNAME).size());
        assertEquals(usersBefore, countUsers());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRegistrations")
    void registerShouldRejectInvalidInputWithoutPersistingAUser(String scenario, Map<String, Object> body,
                                                                String violatedField) {
        long usersBefore = countUsers();

        assertValidationFailure(given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post(REGISTER_PATH)
                .then(), violatedField);

        assertEquals(usersBefore, countUsers());
        assertTrue(findUsers(TEST_USERNAME).isEmpty());
    }

    // ---------- login ----------

    @Test
    void loginShouldReturnABearerJwtValidForOneHourForADemoUser() {
        given()
                .contentType(ContentType.JSON)
                .body(body(DEMO_USERNAME, DEMO_PASSWORD))
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("token", matchesPattern(JWT_PATTERN))
                .body("tokenType", equalTo("Bearer"))
                .body("expiresIn", equalTo(3600));
    }

    @Test
    void loginShouldRejectUnknownUsername() {
        assertInvalidCredentials(given()
                .contentType(ContentType.JSON)
                .body(body("usuario_inexistente", DEMO_PASSWORD))
                .when().post(LOGIN_PATH)
                .then());
    }

    @Test
    void loginShouldRejectWrongPasswordForAnExistingUser() {
        assertInvalidCredentials(given()
                .contentType(ContentType.JSON)
                .body(body(DEMO_USERNAME, "senha-incorreta"))
                .when().post(LOGIN_PATH)
                .then());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidLogins")
    void loginShouldRejectInvalidInputWithoutIssuingAToken(String scenario, Map<String, Object> body,
                                                           String violatedField) {
        assertValidationFailure(given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post(LOGIN_PATH)
                .then(), violatedField)
                .body("token", nullValue());
    }

    // ---------- apoio ----------

    private static void assertInvalidCredentials(ValidatableResponse response) {
        response.statusCode(401)
                .body("status", equalTo(401))
                .body("error", equalTo("Credenciais inválidas."))
                .body("errorId", not(emptyOrNullString()))
                .body("token", nullValue());
    }

    private static ValidatableResponse assertValidationFailure(ValidatableResponse response, String field) {
        return response.statusCode(400)
                .body("violations.field", hasItem(endsWith("." + field)));
    }

    private static Map<String, Object> body(String username, String password) {
        Map<String, Object> body = new HashMap<>();
        if (username != null) {
            body.put("username", username);
        }
        if (password != null) {
            body.put("password", password);
        }
        return body;
    }

    private List<User> findUsers(String username) {
        return QuarkusTransaction.requiringNew().call(() -> repository.list("username", username));
    }

    private long countUsers() {
        return QuarkusTransaction.requiringNew().call(() -> repository.count());
    }
}
