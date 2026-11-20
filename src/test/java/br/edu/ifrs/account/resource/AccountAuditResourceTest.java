package br.edu.ifrs.account.resource;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.edu.ifrs.account.dto.AccountAuditDTO;
import br.edu.ifrs.account.entity.AccountAudit;
import br.edu.ifrs.account.repository.AccountAuditRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AccountAuditResourceTest {

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String AUDIT_PATH = "/api/v1/audit";

    private static final Long OWNER_A = 990_001L;
    private static final Long OWNER_B = 990_002L;
    private static final Long OWNER_C = 990_003L;
    private static final Long OWNER_WITHOUT_AUDITS = 990_009L;

    private static final String ACCOUNT_X = "AUDTEST_X";
    private static final String ACCOUNT_Y = "AUDTEST_Y";
    private static final String ACCOUNT_Z = "AUDTEST_Z";
    private static final String ACCOUNT_WITHOUT_AUDITS = "AUDTEST_NONE";

    /** Data fixa e distante no futuro: todas as fixtures são mais recentes que qualquer registro existente. */
    private static final LocalDateTime BASE_TIMESTAMP = LocalDateTime.of(2100, 1, 1, 0, 0, 0);

    private static final Set<String> DTO_FIELDS = Set.of("id", "operationId", "action", "ownerId",
            "sourceAccountId", "targetAccountId", "amount", "result", "timestamp", "errorMessage");

    private record AuditSnapshot(Long id, String operationId, String action, Long ownerId, String sourceAccountId,
                                 String targetAccountId, BigDecimal amount, String result,
                                 LocalDateTime timestamp, String errorMessage) {
    }

    @Inject
    AccountAuditRepository auditRepository;

    /** Fixtures do teste corrente; o índice cresce junto com o timestamp (índice 11 é a mais recente). */
    private List<AccountAudit> fixtures;
    private Map<Long, AuditSnapshot> auditsBeforeRequest;

    @BeforeEach
    void prepareFixtures() {
        fixtures = new ArrayList<>();
        // conta X como destino
        fixtures.add(fixture(0, "DEPOSIT", OWNER_A, null, ACCOUNT_X, "10.00", "SUCCESS", null));
        // conta X como origem
        fixtures.add(fixture(1, "TRANSFER", OWNER_A, ACCOUNT_X, ACCOUNT_Y, "20.00", "SUCCESS", null));
        // conta X como destino em outra transferência
        fixtures.add(fixture(2, "TRANSFER", OWNER_A, ACCOUNT_Y, ACCOUNT_X, "30.50", "SUCCESS", null));
        // conta X simultaneamente origem e destino
        fixtures.add(fixture(3, "TRANSFER", OWNER_B, ACCOUNT_X, ACCOUNT_X, "5.00", "FAILURE",
                "Conta de origem e destino não podem ser iguais."));
        // falha sem relação com a conta X
        fixtures.add(fixture(4, "WITHDRAW", OWNER_B, null, ACCOUNT_Z, "40.00", "FAILURE", "Saldo insuficiente."));
        for (int i = 5; i <= 11; i++) {
            fixtures.add(fixture(i, "DEPOSIT", OWNER_C, null, ACCOUNT_Z, i + ".25", "SUCCESS", null));
        }

        QuarkusTransaction.requiringNew().run(() -> fixtures.forEach(auditRepository::persist));
        auditsBeforeRequest = snapshotAudits();
    }

    @AfterEach
    void removeFixtures() {
        if (fixtures != null && !fixtures.isEmpty()) {
            List<String> operationIds = fixtures.stream().map(AccountAudit::getOperationId).toList();
            QuarkusTransaction.requiringNew().run(() -> auditRepository.delete("operationId in ?1", operationIds));
        }
    }

    // ---------- listagem ----------

    @Test
    void listAllShouldReturnEveryPersistedAuditWithAllFieldsAndNotModifyThem() {
        Response response = getOk(AUDIT_PATH);
        List<AccountAuditDTO> body = dtos(response);

        assertEquals(auditsBeforeRequest.size(), body.size());
        assertEquals(operationIdsOf(fixtures).size(), fixtures.size());
        assertTrue(operationIdsOf(body).containsAll(operationIdsOf(fixtures)));
        assertEquals(DTO_FIELDS, response.jsonPath().getMap("find { it.operationId == '"
                + fixtures.get(0).getOperationId() + "' }").keySet());

        assertMatchesFixture(byOperationId(body, fixtures.get(0)), fixtures.get(0));
        assertMatchesFixture(byOperationId(body, fixtures.get(3)), fixtures.get(3));
        assertAuditsUnchanged();
    }

    @Test
    void recentShouldReturnTheTwoNewestAuditsInDescendingOrderWhenLimitIsTwo() {
        List<AccountAuditDTO> body = dtos(getOk(AUDIT_PATH + "/recent?limit=2"));

        assertEquals(List.of(fixtures.get(11).getOperationId(), fixtures.get(10).getOperationId()),
                operationIdsInOrder(body));
        assertDescendingTimestamps(body);
        assertAuditsUnchanged();
    }

    @Test
    void recentShouldReturnTheTenNewestAuditsWhenNoLimitIsGiven() {
        List<AccountAuditDTO> body = dtos(getOk(AUDIT_PATH + "/recent"));

        List<String> expected = new ArrayList<>();
        for (int i = 11; i >= 2; i--) {
            expected.add(fixtures.get(i).getOperationId());
        }
        assertEquals(10, body.size());
        assertEquals(expected, operationIdsInOrder(body));
        assertFalse(operationIdsOf(body).contains(fixtures.get(0).getOperationId()));
        assertFalse(operationIdsOf(body).contains(fixtures.get(1).getOperationId()));
        assertDescendingTimestamps(body);
        assertAuditsUnchanged();
    }

    @Test
    void recentShouldReturnEveryPersistedAuditInDescendingOrderWhenLimitExceedsTheTotal() {
        int limit = auditsBeforeRequest.size() + 1;

        List<AccountAuditDTO> body = dtos(getOk(AUDIT_PATH + "/recent?limit=" + limit));

        assertEquals(auditsBeforeRequest.size(), body.size());
        assertEquals(auditsBeforeRequest.keySet(), body.stream().map(AccountAuditDTO::id).collect(Collectors.toSet()));
        assertDescendingTimestamps(body);
        assertAuditsUnchanged();
    }

    // ---------- filtro por proprietário ----------

    @Test
    void byOwnerShouldReturnOnlyTheAuditsOfThatOwner() {
        List<AccountAuditDTO> body = dtos(getOk(AUDIT_PATH + "/owner/" + OWNER_A));

        assertEquals(operationIdsOf(fixtures.subList(0, 3)), operationIdsOf(body));
        assertEquals(3, body.size());
        assertTrue(body.stream().allMatch(dto -> OWNER_A.equals(dto.ownerId())));
        assertAuditsUnchanged();
    }

    @Test
    void byOwnerShouldReturnAnEmptyListWhenTheOwnerHasNoAudits() {
        Response response = getOk(AUDIT_PATH + "/owner/" + OWNER_WITHOUT_AUDITS);

        assertEquals(0, dtos(response).size());
        assertEquals("[]", response.asString().replaceAll("\\s", ""));
        assertAuditsUnchanged();
    }

    // ---------- filtro por conta ----------

    @Test
    void byAccountShouldReturnAuditsWhereTheAccountIsSourceOrTargetWithoutDuplicates() {
        List<AccountAuditDTO> body = dtos(getOk(AUDIT_PATH + "/account/" + ACCOUNT_X));

        // fixtures 0 (destino), 1 (origem), 2 (destino) e 3 (origem e destino, uma única vez)
        assertEquals(4, body.size());
        assertEquals(operationIdsOf(fixtures.subList(0, 4)), operationIdsOf(body));
        assertEquals(4, operationIdsOf(body).size());
        assertAuditsUnchanged();
    }

    @Test
    void byAccountShouldReturnAnEmptyListWhenTheAccountHasNoAudits() {
        Response response = getOk(AUDIT_PATH + "/account/" + ACCOUNT_WITHOUT_AUDITS);

        assertEquals(0, dtos(response).size());
        assertEquals("[]", response.asString().replaceAll("\\s", ""));
        assertAuditsUnchanged();
    }

    // ---------- helpers ----------

    private static AccountAudit fixture(int index, String action, Long ownerId, String source, String target,
                                        String amount, String result, String errorMessage) {
        return new AccountAudit(UUID.randomUUID().toString(), action, ownerId, source, target,
                new BigDecimal(amount), result, BASE_TIMESTAMP.plusMinutes(index), errorMessage);
    }

    private static String auditorToken() {
        return given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", "auditor", "password", "auditor123"))
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(200)
                .extract().path("token");
    }

    private static Response getOk(String path) {
        return given()
                .auth().oauth2(auditorToken())
                .when().get(path)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response();
    }

    private static List<AccountAuditDTO> dtos(Response response) {
        return Arrays.asList(response.as(AccountAuditDTO[].class));
    }

    private static Set<String> operationIdsOf(List<?> items) {
        return items.stream()
                .map(item -> item instanceof AccountAudit audit
                        ? audit.getOperationId()
                        : ((AccountAuditDTO) item).operationId())
                .collect(Collectors.toSet());
    }

    private static List<String> operationIdsInOrder(List<AccountAuditDTO> body) {
        return body.stream().map(AccountAuditDTO::operationId).toList();
    }

    private static AccountAuditDTO byOperationId(List<AccountAuditDTO> body, AccountAudit fixture) {
        return body.stream()
                .filter(dto -> dto.operationId().equals(fixture.getOperationId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Auditoria ausente: " + fixture.getOperationId()));
    }

    private static void assertMatchesFixture(AccountAuditDTO dto, AccountAudit fixture) {
        assertEquals(fixture.getId(), dto.id());
        assertEquals(fixture.getOperationId(), dto.operationId());
        assertEquals(fixture.getAction(), dto.action());
        assertEquals(fixture.getOwnerId(), dto.ownerId());
        assertEquals(fixture.getSourceAccountId(), dto.sourceAccountId());
        assertEquals(fixture.getTargetAccountId(), dto.targetAccountId());
        assertNotNull(dto.amount());
        assertEquals(0, fixture.getAmount().compareTo(dto.amount()),
                "esperado " + fixture.getAmount() + " mas foi " + dto.amount());
        assertEquals(fixture.getResult(), dto.result());
        assertEquals(fixture.getTimestamp(), dto.timestamp());
        if (fixture.getErrorMessage() == null) {
            assertNull(dto.errorMessage());
        } else {
            assertEquals(fixture.getErrorMessage(), dto.errorMessage());
        }
    }

    private static void assertDescendingTimestamps(List<AccountAuditDTO> body) {
        // timestamps iguais podem aparecer em qualquer ordem; só a não-crescência é exigida
        for (int i = 1; i < body.size(); i++) {
            assertFalse(body.get(i - 1).timestamp().isBefore(body.get(i).timestamp()),
                    "ordem não decrescente na posição " + i);
        }
    }

    private Map<Long, AuditSnapshot> snapshotAudits() {
        return QuarkusTransaction.requiringNew().call(() -> auditRepository.listAll().stream()
                .map(a -> new AuditSnapshot(a.getId(), a.getOperationId(), a.getAction(), a.getOwnerId(),
                        a.getSourceAccountId(), a.getTargetAccountId(), a.getAmount(), a.getResult(),
                        a.getTimestamp(), a.getErrorMessage()))
                .collect(Collectors.toMap(AuditSnapshot::id, Function.identity())));
    }

    private void assertAuditsUnchanged() {
        assertEquals(auditsBeforeRequest, snapshotAudits());
    }
}
