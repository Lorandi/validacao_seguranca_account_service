package br.edu.ifrs.account.auth;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.mindrot.jbcrypt.BCrypt;

import java.util.Set;

@ApplicationScoped
public class AuthService {

    private static final String ISSUER = "users-issuer";
    private static final long TOKEN_LIFESPAN_SECONDS = 3600;

    @Inject
    UserRepository repository;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String username = request.username().trim();
        if (repository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Nome de usuário já cadastrado.");
        }

        String passwordHash = BCrypt.hashpw(request.password(), BCrypt.gensalt(12));
        User user = new User(username, passwordHash, UserRole.CUSTOMER);
        repository.persistAndFlush(user);

        return new UserResponse(user.getId(), user.getUsername(), user.getRole());
    }

    public TokenResponse login(LoginRequest request) {
        User user = repository.findByUsername(request.username().trim())
                .orElseThrow(() -> new InvalidCredentialsException("Credenciais inválidas."));

        if (!BCrypt.checkpw(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Credenciais inválidas.");
        }

        String token = Jwt.issuer(ISSUER)
                .upn(user.getUsername())
                .subject(user.getId().toString())
                .groups(Set.of(user.getRole().name()))
                .claim("userId", user.getId())
                .sign();

        return new TokenResponse(token, "Bearer", TOKEN_LIFESPAN_SECONDS);
    }
}
