package br.edu.ifrs.account.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import br.edu.ifrs.account.auth.InvalidCredentialsException;
import jakarta.ws.rs.NotSupportedException;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ApiExceptionMapperTest {

    private final ApiExceptionMapper mapper = new ApiExceptionMapper();

    static Stream<Arguments> exceptionsAndExpectedStatus() {
        return Stream.of(
                Arguments.of(new InvalidCredentialsException("Credenciais inválidas."), 401),
                Arguments.of(new IllegalArgumentException("O valor deve ser maior que zero."), 400),
                Arguments.of(new IllegalStateException("Saldo insuficiente."), 409),
                Arguments.of(new RuntimeException("Falha inesperada"), 500));
    }

    static Stream<Arguments> exceptionsWithoutMessageAndExpectedStatus() {
        return Stream.of(
                Arguments.of(new InvalidCredentialsException(null), 401),
                Arguments.of(new IllegalArgumentException(), 400),
                Arguments.of(new IllegalStateException(), 409),
                Arguments.of(new RuntimeException(), 500));
    }

    @ParameterizedTest
    @MethodSource("exceptionsWithoutMessageAndExpectedStatus")
    void toResponseShouldUseADefaultMessageWhenTheExceptionHasNoMessage(Exception exception, int expectedStatus) {
        try (Response response = mapper.toResponse(exception)) {
            Map<String, Object> body = body(response);

            assertEquals(expectedStatus, response.getStatus());
            assertEquals(Set.of("errorId", "status", "error"), body.keySet());
            assertEquals("Erro inesperado.", body.get("error"));
        }
    }

    @ParameterizedTest
    @MethodSource("exceptionsAndExpectedStatus")
    void toResponseShouldMapTheExceptionTypeToTheExpectedHttpStatus(Exception exception, int expectedStatus) {
        try (Response response = mapper.toResponse(exception)) {
            assertEquals(expectedStatus, response.getStatus());
            assertEquals(expectedStatus, body(response).get("status"));
        }
    }

    @Test
    void toResponseShouldReturnErrorIdStatusAndOriginalMessageInTheBody() {
        try (Response response = mapper.toResponse(new IllegalArgumentException("O valor deve ser maior que zero."))) {
            Map<String, Object> body = body(response);

            assertEquals(Set.of("errorId", "status", "error"), body.keySet());
            assertEquals(400, body.get("status"));
            assertEquals("O valor deve ser maior que zero.", body.get("error"));
        }
    }

    @Test
    void toResponseShouldGenerateAnErrorIdInUuidFormat() {
        try (Response response = mapper.toResponse(new IllegalStateException("Conta bloqueada: ACC1005"))) {
            String errorId = (String) body(response).get("errorId");

            assertFalse(errorId.isBlank());
            assertEquals(errorId, UUID.fromString(errorId).toString());
        }
    }

    @Test
    void toResponseShouldNotExposeTheInternalMessageOfAnUnexpectedException() {
        String internalMessage = "could not execute statement [select * from account where id = ?] at /opt/app/db";

        try (Response response = mapper.toResponse(new RuntimeException(internalMessage))) {
            assertEquals(500, response.getStatus());
            assertEquals("Erro inesperado.", body(response).get("error"));
        }
    }

    @Test
    void toResponseShouldPreserveTheOriginalMessageOfAuthenticationAndConflictErrors() {
        try (Response unauthorized = mapper.toResponse(new InvalidCredentialsException("Credenciais inválidas."));
             Response conflict = mapper.toResponse(new IllegalStateException("Saldo insuficiente."))) {
            assertEquals("Credenciais inválidas.", body(unauthorized).get("error"));
            assertEquals("Saldo insuficiente.", body(conflict).get("error"));
        }
    }

    @Test
    void toResponseShouldMapNotSupportedExceptionTo415WithAControlledMessage() {
        String internalMessage = "RESTEASY003065: Cannot consume content type text/plain at /api/v1/accounts";

        try (Response response = mapper.toResponse(new NotSupportedException(internalMessage))) {
            Map<String, Object> body = body(response);
            String errorId = (String) body.get("errorId");

            assertEquals(415, response.getStatus());
            assertEquals(Set.of("errorId", "status", "error"), body.keySet());
            assertEquals(415, body.get("status"));
            assertEquals("Tipo de conteúdo não suportado.", body.get("error"));
            assertEquals(errorId, UUID.fromString(errorId).toString());
            assertFalse(body.values().toString().contains("RESTEASY003065"));
            assertFalse(body.values().toString().contains("/api/v1/accounts"));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> body(Response response) {
        return (Map<String, Object>) response.getEntity();
    }
}
