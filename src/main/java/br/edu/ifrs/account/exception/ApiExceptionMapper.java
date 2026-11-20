package br.edu.ifrs.account.exception;

import jakarta.ws.rs.core.Response;
import br.edu.ifrs.account.auth.InvalidCredentialsException;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;


@Provider
public class ApiExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(ApiExceptionMapper.class);
    private static final String DEFAULT_MESSAGE = "Erro inesperado.";

    @Override
    public Response toResponse(Exception e) {
        String errorId = UUID.randomUUID().toString();
        int status = switch (e) {
            case InvalidCredentialsException ignored -> Response.Status.UNAUTHORIZED.getStatusCode();
            case IllegalArgumentException ignored -> Response.Status.BAD_REQUEST.getStatusCode();
            case IllegalStateException ignored -> Response.Status.CONFLICT.getStatusCode();
            default -> Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
        };

        if (status >= 500) {
            LOG.errorf("ErrorID=%s | Type=%s | Message=%s", errorId, e.getClass().getSimpleName(), e.getMessage());
        } else {
            LOG.warnf("ErrorID=%s | Status=%d | Message=%s", errorId, status, e.getMessage());
        }

        String message = status >= 500
                ? DEFAULT_MESSAGE
                : Objects.requireNonNullElse(e.getMessage(), DEFAULT_MESSAGE);

        return Response.status(status)
                .entity(Map.of(
                        "errorId", errorId,
                        "status", status,
                        "error", message
                ))
                .build();
    }
}
