package br.edu.ifrs.account.auth;

public record TokenResponse(String token, String tokenType, long expiresIn) {
}
