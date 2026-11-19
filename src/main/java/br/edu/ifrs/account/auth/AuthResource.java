package br.edu.ifrs.account.auth;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;

@Path("/api/v1/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@PermitAll
public class AuthResource {

    @Inject
    AuthService service;

    @POST
    @Path("/register")
    @Operation(summary = "Cadastra um cliente")
    public Response register(@Valid RegisterRequest request) {
        UserResponse user = service.register(request);
        return Response.status(Response.Status.CREATED).entity(user).build();
    }

    @POST
    @Path("/login")
    @Operation(summary = "Autentica um usuário e emite um JWT")
    public TokenResponse login(@Valid LoginRequest request) {
        return service.login(request);
    }
}
