package br.edu.ifrs.account.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.smallrye.jwt.build.Jwt;
import io.smallrye.jwt.build.JwtClaimsBuilder;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mindrot.jbcrypt.BCrypt;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String PASSWORD = "senha-segura-123";
    private static final String INVALID_CREDENTIALS = "Credenciais inválidas.";

    @Mock
    UserRepository repository;

    @InjectMocks
    AuthService service;

    // ---------- register ----------

    @Test
    void registerShouldPersistACustomerWithTrimmedUsernameAndBcryptHash() {
        when(repository.findByUsername("maria")).thenReturn(Optional.empty());

        UserResponse response = service.register(new RegisterRequest("  maria  ", PASSWORD));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repository).persistAndFlush(captor.capture());
        User persisted = captor.getValue();
        assertEquals("maria", persisted.getUsername());
        assertEquals(UserRole.CUSTOMER, persisted.getRole());
        assertNotEquals(PASSWORD, persisted.getPasswordHash());
        assertTrue(BCrypt.checkpw(PASSWORD, persisted.getPasswordHash()));
        assertEquals("maria", response.username());
        assertEquals(UserRole.CUSTOMER, response.role());
    }

    @Test
    void registerShouldRejectAlreadyRegisteredUsernameWithoutPersisting() {
        when(repository.findByUsername("maria"))
                .thenReturn(Optional.of(new User("maria", "hash", UserRole.CUSTOMER)));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.register(new RegisterRequest(" maria ", PASSWORD)));

        assertEquals("Nome de usuário já cadastrado.", exception.getMessage());
        verify(repository, never()).persistAndFlush(any(User.class));
    }

    // ---------- login ----------

    @Test
    void loginShouldRejectUnknownUsername() {
        when(repository.findByUsername("ghost")).thenReturn(Optional.empty());

        InvalidCredentialsException exception = assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequest("ghost", PASSWORD)));

        assertEquals(INVALID_CREDENTIALS, exception.getMessage());
    }

    @Test
    void loginShouldRejectWrongPassword() {
        User user = new User("maria", BCrypt.hashpw(PASSWORD, BCrypt.gensalt(4)), UserRole.CUSTOMER);
        when(repository.findByUsername("maria")).thenReturn(Optional.of(user));

        InvalidCredentialsException exception = assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequest("maria", "outra-senha-123")));

        assertEquals(INVALID_CREDENTIALS, exception.getMessage());
    }

    @Test
    void loginShouldReturnABearerTokenValidForOneHourBuiltWithTheUserClaims() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        when(user.getUsername()).thenReturn("maria");
        when(user.getPasswordHash()).thenReturn(BCrypt.hashpw(PASSWORD, BCrypt.gensalt(4)));
        when(user.getRole()).thenReturn(UserRole.ADMIN);
        when(repository.findByUsername("maria")).thenReturn(Optional.of(user));
        JwtClaimsBuilder builder = mock(JwtClaimsBuilder.class, RETURNS_SELF);
        when(builder.sign()).thenReturn("signed-token");

        try (MockedStatic<Jwt> jwt = mockStatic(Jwt.class)) {
            jwt.when(() -> Jwt.issuer("users-issuer")).thenReturn(builder);

            TokenResponse response = service.login(new LoginRequest(" maria ", PASSWORD));

            assertEquals(new TokenResponse("signed-token", "Bearer", 3600), response);
            jwt.verify(() -> Jwt.issuer("users-issuer"));
        }

        verify(builder).upn("maria");
        verify(builder).subject("7");
        verify(builder).groups(Set.of("ADMIN"));
        verify(builder).claim("userId", 7L);
        verify(builder).sign();
    }
}
