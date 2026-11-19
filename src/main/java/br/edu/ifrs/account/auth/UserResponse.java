package br.edu.ifrs.account.auth;

public record UserResponse(Long id, String username, UserRole role) {
}
